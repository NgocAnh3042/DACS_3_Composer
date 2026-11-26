package com.example.backend.payment;

import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.payment.PaymentDtos.CreateLinkRequest;
import com.example.backend.payment.PaymentDtos.CreateLinkResponse;
import com.example.backend.payment.PaymentDtos.IgnoredWebhookResponse;
import com.example.backend.payment.PaymentDtos.PaymentItem;
import com.example.backend.payment.PaymentDtos.WebhookResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {
    private final PaymentRepository repository;private final PayosClient client;
    private final ObjectMapper json;private final org.springframework.context.ApplicationEventPublisher events;
    private final String checksumKey,returnUrl,cancelUrl;private final SecureRandom random=new SecureRandom();
    public PaymentService(PaymentRepository repository,PayosClient client,ObjectMapper json,
            org.springframework.context.ApplicationEventPublisher events,
            @Value("${PAYOS_CHECKSUM_KEY:}") String checksumKey,@Value("${PAYOS_RETURN_URL:}") String returnUrl,
            @Value("${PAYOS_CANCEL_URL:}") String cancelUrl,@Value("${PAYOS_CLIENT_ID:}") String clientId,
            @Value("${PAYOS_API_KEY:}") String apiKey){
        this.repository=repository;this.client=client;this.json=json;this.events=events;
        this.checksumKey=checksumKey;this.returnUrl=returnUrl;this.cancelUrl=cancelUrl;
        this.clientId=clientId;this.apiKey=apiKey;
    }
    private final String clientId,apiKey;
    public CreateLinkResponse createLink(FirebasePrincipal principal,CreateLinkRequest input){
        long userId=userId(principal);if(input==null||input.orderId()==null||input.orderId()<=0)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Thiếu orderId");
        if(blank(clientId)||blank(apiKey)||blank(returnUrl)||blank(cancelUrl)||blank(checksumKey))throw error(HttpStatus.SERVICE_UNAVAILABLE,"PayOS chưa được cấu hình");
        PaymentRepository.PaymentRow payment=repository.ownedPayosPayment(input.orderId(),userId);
        if(payment==null)throw error(HttpStatus.NOT_FOUND,"Không tìm thấy đơn thanh toán PayOS");
        if(payment.amount().subtract(payment.orderTotal()).abs().compareTo(new BigDecimal("0.01"))>0)throw error(HttpStatus.CONFLICT,"Số tiền phiên thanh toán không khớp đơn");
        if("paid".equals(payment.status()))throw error(HttpStatus.CONFLICT,"Đơn hàng đã thanh toán");
        int amount=payment.amount().setScale(0,java.math.RoundingMode.HALF_UP).intValueExact();
        long orderCode=random.nextLong(100_000_000L,2_147_483_648L);
        String description="FH"+payment.orderId();String signature=PayosSignature.createLinkSignature(amount,cancelUrl,description,orderCode,returnUrl,checksumKey);
        PayosClient.PayosCreateResult result;
        try{result=client.createPaymentLink(new PayosClient.PayosCreateRequest(orderCode,amount,description,cancelUrl,returnUrl,signature));}
        catch(RuntimeException exception){throw error(HttpStatus.BAD_GATEWAY,"PayOS không tạo được link thanh toán");}
        if(result==null||!"00".equals(result.code())||blank(result.checkoutUrl())||blank(result.paymentLinkId()))throw error(HttpStatus.BAD_GATEWAY,"PayOS không tạo được link thanh toán");
        if(repository.saveLink(payment.paymentId(),orderCode,result.paymentLinkId(),result.checkoutUrl())==0)
            throw error(HttpStatus.CONFLICT,"Đơn hàng đã thanh toán");
        return new CreateLinkResponse(result.checkoutUrl(),result.paymentLinkId(),result.qrCode()==null?"":result.qrCode());
    }
    public List<PaymentItem> list(FirebasePrincipal principal){return repository.listForCustomer(userId(principal),principal.firebaseUid());}

    @Transactional
    public Object webhook(Map<String,Object> payload){
        if(blank(checksumKey))throw error(HttpStatus.SERVICE_UNAVAILABLE,"PayOS chưa được cấu hình");
        Object rawData=payload==null?null:payload.get("data");Object rawSignature=payload==null?null:payload.get("signature");
        if(!(rawData instanceof Map<?,?> map)||!(rawSignature instanceof String signature)||signature.isBlank())throw error(HttpStatus.BAD_REQUEST,"PayOS webhook payload không hợp lệ");
        Map<String,Object> data=new java.util.LinkedHashMap<>();for(var entry:map.entrySet())if(entry.getKey() instanceof String key)data.put(key,entry.getValue());
        if(!PayosSignature.verifyWebhook(data,signature,checksumKey,json))throw error(HttpStatus.UNAUTHORIZED,"PayOS signature không hợp lệ");
        if(!Boolean.TRUE.equals(payload.get("success"))||!"00".equals(string(data.get("code"))))return new IgnoredWebhookResponse(true,true);
        String linkId=string(data.get("paymentLinkId"));if(blank(linkId))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Webhook thiếu paymentLinkId");
        PaymentRepository.WebhookPayment payment=repository.lockByPaymentLink(linkId);if(payment==null)return new IgnoredWebhookResponse(true,true);
        BigDecimal amount=decimal(data.get("amount"));
        if(amount==null||amount.subtract(payment.amount()).abs().compareTo(new BigDecimal("0.01"))>0
                ||amount.subtract(payment.orderTotal()).abs().compareTo(new BigDecimal("0.01"))>0)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Số tiền webhook không khớp đơn hàng");
        String payloadHash=sha256(json.writeValueAsString(payload));String reference=string(data.get("reference"));
        String eventId=blank(reference)?"payos-"+linkId+"-"+payloadHash:reference;
        if(!repository.insertWebhookEvent(payment.paymentId(),eventId,payloadHash,"payment.succeeded"))return new IgnoredWebhookResponse(true,true);
        if(repository.markPaidIfUnpaid(payment,blank(reference)?null:reference)>0){
            repository.markOrderPaid(payment.orderId());
            if("pending_payment".equals(payment.orderStatus()))repository.insertPendingStatusLog(payment.orderId());
            events.publishEvent(new PaymentSettledEvent(payment.customerId(),payment.ownerId(),payment.orderId()));
        }
        return new WebhookResponse(true);
    }
    private long userId(FirebasePrincipal p){if(p==null||p.userId()==null)throw error(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ tài khoản");return p.userId();}
    private static ApiException error(HttpStatus status,String message){return new ApiException(status,message);}
    private static boolean blank(String value){return value==null||value.isBlank();}
    private static String string(Object value){return value==null?"":String.valueOf(value);}
    private static BigDecimal decimal(Object value){try{return value==null?null:new BigDecimal(String.valueOf(value));}catch(NumberFormatException exception){return null;}}
    private static String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
