package com.example.backend.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PayosHttpClient implements PayosClient {
    private static final URI ENDPOINT=URI.create("https://api-merchant.payos.vn/v2/payment-requests");
    private final ObjectMapper json;
    private final HttpClient http;
    private final String clientId,apiKey,checksumKey;
    public PayosHttpClient(ObjectMapper json,@Value("${PAYOS_CLIENT_ID:}") String clientId,
            @Value("${PAYOS_API_KEY:}") String apiKey,@Value("${PAYOS_CHECKSUM_KEY:}") String checksumKey){
        this.json=json;this.clientId=clientId;this.apiKey=apiKey;this.checksumKey=checksumKey;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    public boolean configured(){return !blank(clientId)&&!blank(apiKey)&&!blank(checksumKey);}
    @Override public PayosCreateResult createPaymentLink(PayosCreateRequest input){
        try {
            Map<String,Object> body=new LinkedHashMap<>();body.put("orderCode",input.orderCode());body.put("amount",input.amount());
            body.put("description",input.description());body.put("cancelUrl",input.cancelUrl());body.put("returnUrl",input.returnUrl());body.put("signature",input.signature());
            HttpRequest request=HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(10))
                    .header("x-client-id",clientId).header("x-api-key",apiKey).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)return null;
            JsonNode root=json.readTree(response.body());JsonNode data=root.path("data");
            return new PayosCreateResult(root.path("code").asString(""),data.path("checkoutUrl").asString(""),
                    data.path("paymentLinkId").asString(""),data.path("qrCode").asString(""));
        }catch(Exception exception){return null;}
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
}
