package com.example.backend.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

final class PayosSignature {
    private PayosSignature(){}
    static String createLinkSignature(int amount,String cancelUrl,String description,long orderCode,String returnUrl,String key){
        return hmac("amount="+amount+"&cancelUrl="+cancelUrl+"&description="+description+"&orderCode="+orderCode+"&returnUrl="+returnUrl,key);
    }
    static boolean verifyWebhook(Map<String,Object> data,String signature,String key,ObjectMapper json){
        TreeMap<String,Object> sorted=new TreeMap<>(data);StringBuilder canonical=new StringBuilder();
        for(var entry:sorted.entrySet()){
            if(canonical.length()>0)canonical.append('&');canonical.append(entry.getKey()).append('=').append(normalize(entry.getValue(),json));
        }
        byte[] expected=hex(hmac(canonical.toString(),key));byte[] actual=hex(signature);
        return expected!=null&&actual!=null&&MessageDigest.isEqual(expected,actual);
    }
    private static String normalize(Object value,ObjectMapper json){
        if(value==null)return "";
        if(value instanceof String s&&(s.equals("null")||s.equals("undefined")))return "";
        if(value instanceof Boolean b)return b?"true":"false";
        if(value instanceof Number number)try{return new java.math.BigDecimal(number.toString()).stripTrailingZeros().toPlainString();}catch(NumberFormatException ignored){return number.toString();}
        if(value instanceof Map<?,?>||value instanceof java.util.List<?>)try{return json.writeValueAsString(value);}catch(Exception ignored){return "";}
        return String.valueOf(value);
    }
    private static String hmac(String value,String key){
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception exception){throw new IllegalStateException("HMAC unavailable",exception);}
    }
    private static byte[] hex(String value){try{return java.util.HexFormat.of().parseHex(value);}catch(RuntimeException exception){return null;}}
}
