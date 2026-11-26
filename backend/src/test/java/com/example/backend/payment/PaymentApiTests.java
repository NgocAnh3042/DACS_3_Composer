package com.example.backend.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.auth.FirebaseTokenVerifier;
import com.example.backend.notification.FcmClient;
import com.example.backend.notification.FcmSendResult;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:foodhubpaymenttest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "PAYOS_CLIENT_ID=test-client-id","PAYOS_API_KEY=test-api-key","PAYOS_CHECKSUM_KEY=test-checksum-key",
    "PAYOS_RETURN_URL=dacs3://payment_callback?status=PAID","PAYOS_CANCEL_URL=dacs3://payment_callback?status=CANCELLED"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc @ActiveProfiles("test")
class PaymentApiTests {
    private static final String CUSTOMER="payment-customer", OTHER="payment-other";
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired JsonMapper json;
    @MockitoBean FirebaseTokenVerifier verifier;@MockitoBean PayosClient payos;@MockitoBean FcmClient fcmClient;

    @BeforeEach void seed(){
        jdbc.update("DELETE FROM payment_webhook_events");jdbc.update("DELETE FROM notifications");jdbc.update("DELETE FROM user_device_tokens");jdbc.update("DELETE FROM order_status_logs");
        jdbc.update("DELETE FROM payments");jdbc.update("DELETE FROM deliveries");jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM stores");jdbc.update("DELETE FROM merchants");jdbc.update("DELETE FROM users");
        jdbc.update("ALTER TABLE payments ALTER COLUMN id RESTART WITH 1");
        jdbc.update("INSERT INTO users(id,firebase_uid,name,email,role,status) VALUES(1,'payment-uid','Customer','customer@test.invalid','customer','active'),(2,'other-payment-uid','Other','other@test.invalid','customer','active'),(3,'seller-uid','Seller','seller@test.invalid','seller','active')");
        jdbc.update("INSERT INTO merchants(id,owner_user_id,brand_name) VALUES(1,3,'Merchant')");
        jdbc.update("INSERT INTO stores(id,merchant_id,name,is_open,status) VALUES(1,1,'Test store',TRUE,'active')");
        jdbc.update("INSERT INTO orders(id,code,customer_id,store_id,status,receiver_name,receiver_phone,delivery_address,subtotal,delivery_fee,discount_amount,total_amount,payment_method,payment_status) VALUES(1,'PAY-1',1,1,'pending_payment','Customer','0900','Test',50000,10000,0,60000,'payos','pending'),(2,'PAY-2',2,1,'pending_payment','Other','0901','Test',50000,10000,0,60000,'payos','pending'),(3,'PAY-3',1,1,'pending_payment','Customer','0900','Test',50000,10000,0,60000,'cash','pending'),(4,'PAY-4',1,1,'pending','Customer','0900','Test',50000,10000,0,60000,'payos','paid')");
        jdbc.update("INSERT INTO payments(id,order_id,provider,method,payos_order_code,payment_link_id,amount,status) VALUES(1,1,'payos','payos',NULL,'link-1',60000,'pending'),(2,2,'payos','payos',NULL,'link-2',60000,'pending'),(3,3,'cash','cash',NULL,NULL,60000,'pending'),(4,4,'payos','payos',NULL,'link-4',60000,'paid')");
        jdbc.update("INSERT INTO user_device_tokens(user_id,fcm_token,platform) VALUES(1,'customer-device','android'),(3,'seller-device','android')");
        claims(CUSTOMER,"payment-uid");claims(OTHER,"other-payment-uid");
        when(payos.createPaymentLink(any())).thenReturn(new PayosClient.PayosCreateResult("00","https://checkout.test/link","generated-link","qr-test"));
        when(fcmClient.send(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(FcmSendResult.SENT);
    }

    @Test void createLinkSignsCanonicalRequestAndPersistsProcessing() throws Exception{
        MvcResult result=performCreate(CUSTOMER,1).andExpect(status().isOk()).andReturn();
        JsonNode body=read(result);assertEquals(fixture("fixtures/payos-create-link.json"),body);
        assertEquals("generated-link",body.get("paymentLinkId").stringValue());assertEquals("qr-test",body.get("qrCode").stringValue());
        assertEquals("processing",jdbc.queryForObject("SELECT status FROM payments WHERE id=1",String.class));
        org.mockito.ArgumentCaptor<PayosClient.PayosCreateRequest> captor=org.mockito.ArgumentCaptor.forClass(PayosClient.PayosCreateRequest.class);
        org.mockito.Mockito.verify(payos).createPaymentLink(captor.capture());var request=captor.getValue();
        assertEquals(PayosSignature.createLinkSignature(request.amount(),request.cancelUrl(),request.description(),request.orderCode(),request.returnUrl(),"test-checksum-key"),request.signature());
    }

    @Test void createLinkRejectsForeignCashAndPaidOrders() throws Exception{
        performCreate(CUSTOMER,2).andExpect(status().isNotFound());
        performCreate(CUSTOMER,3).andExpect(status().isNotFound());
        performCreate(CUSTOMER,4).andExpect(status().isConflict());
        performCreate(OTHER,1).andExpect(status().isNotFound());
    }

    @Test void createLinkRejectsDatabaseAmountMismatch() throws Exception{
        jdbc.update("UPDATE payments SET amount=59999 WHERE id=1");
        performCreate(CUSTOMER,1).andExpect(status().isConflict());
        org.mockito.Mockito.verifyNoInteractions(payos);
    }

    @Test void missingPayosConfigurationReturns503WithoutCallingProvider(){
        PaymentService unconfigured=new PaymentService(new PaymentRepository(jdbc),payos,json,
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),"","","","","");
        var principal=new com.example.backend.auth.FirebasePrincipal("payment-uid",1L,"customer","user","customer@test.invalid",true);
        var failure=assertThrows(com.example.backend.api.ApiException.class,()->unconfigured.createLink(principal,new PaymentDtos.CreateLinkRequest(1L)));
        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,failure.getStatus());
        org.mockito.Mockito.verifyNoInteractions(payos);
    }

    @Test void paymentsListIsScopedToCurrentCustomerAndLegacyPostIsNotMapped() throws Exception{
        JsonNode response=read(mvc.perform(get("/api/payments").header("Authorization","Bearer "+CUSTOMER))
                .andExpect(status().isOk()).andReturn());
        assertEquals(3,response.get("data").size());
        assertTrue(response.at("/data/0/id").isString());
        assertEquals("payment-uid",response.at("/data/0/userId").stringValue());
        mvc.perform(post("/api/payments").header("Authorization","Bearer "+CUSTOMER).contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test void validWebhookMatchesFixtureAndUpdatesPaymentOrderAndLog() throws Exception{
        JsonNode actual=read(performWebhook(webhook(1,"reference-1","00",true)).andExpect(status().isOk()).andReturn());
        assertEquals(fixture("fixtures/payos-webhook-ok.json"),actual);
        assertEquals("paid",jdbc.queryForObject("SELECT status FROM payments WHERE id=1",String.class));
        assertEquals("paid",jdbc.queryForObject("SELECT payment_status FROM orders WHERE id=1",String.class));
        assertEquals("pending",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_status_logs WHERE order_id=1",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE related_order_id=1",Integer.class));
        verify(fcmClient,timeout(3000).times(2)).send(anyString(),anyString(),anyString(),anyString(),anyString());
    }

    @Test void rejectsInvalidSignatureAndAmountMismatch() throws Exception{
        String valid=webhook(1,"bad-signature","00",true);JsonNode parsed=json.readTree(valid);String malformed=valid.replace(parsed.get("signature").stringValue(),"00");
        performWebhook(malformed).andExpect(status().isUnauthorized());
        performWebhook(webhook(1,"wrong-amount","00",true).replace("\"amount\":60000","\"amount\":59000"))
                .andExpect(status().isUnauthorized());
        // Sign a correctly authenticated webhook carrying the wrong amount to reach amount validation.
        performWebhook(signedWebhook(59000,"wrong-amount","00",true,"link-1")).andExpect(status().isUnprocessableEntity());
        assertEquals("pending",jdbc.queryForObject("SELECT status FROM payments WHERE id=1",String.class));
    }

    @Test void duplicateWebhookIsIdempotentAndAlreadyPaidPaymentIsNotProcessedAgain() throws Exception{
        String payload=signedWebhook(60000,"same-event","00",true,"link-1");
        performWebhook(payload).andExpect(status().isOk());performWebhook(payload).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events WHERE provider_event_id='same-event'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE related_order_id=1",Integer.class));
        String alreadyPaid=signedWebhook(60000,"paid-event","00",true,"link-4");
        performWebhook(alreadyPaid).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE related_order_id=4",Integer.class));
    }

    @Test void simultaneousWebhookRequestsProcessOnlyOnce() throws Exception {
        String payload=signedWebhook(60000,"parallel-event","00",true,"link-1");
        try(var executor=Executors.newFixedThreadPool(2)){
            var one=executor.submit(()->performWebhook(payload).andReturn().getResponse().getStatus());
            var two=executor.submit(()->performWebhook(payload).andReturn().getResponse().getStatus());
            assertEquals(200,one.get());assertEquals(200,two.get());
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events WHERE provider_event_id='parallel-event'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE related_order_id=1",Integer.class));
    }

    @Test void databaseFailureRollsBackWebhookEventAndPayment() throws Exception{
        jdbc.execute("ALTER TABLE payments ADD CONSTRAINT test_reject_paid CHECK(id <> 1 OR status <> 'paid')");
        try{performWebhook(signedWebhook(60000,"rollback-event","00",true,"link-1")).andExpect(status().isServiceUnavailable());}
        finally{jdbc.execute("ALTER TABLE payments DROP CONSTRAINT test_reject_paid");}
        assertEquals("pending",jdbc.queryForObject("SELECT status FROM payments WHERE id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events",Integer.class));
    }

    @Test void webhookDoesNotRequireFirebaseAndNonSuccessIsAcknowledged() throws Exception{
        performWebhook(signedWebhook(60000,"ignored-event","11",true,"link-1")).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events",Integer.class));
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(String token,long orderId){
        try{return mvc.perform(post("/api/payments/create-link").header("Authorization","Bearer "+token).contentType("application/json").content("{\"orderId\":"+orderId+"}"));}
        catch(Exception exception){throw new AssertionError(exception);}
    }
    private org.springframework.test.web.servlet.ResultActions performWebhook(String payload){
        try{return mvc.perform(post("/api/payos/webhook").contentType("application/json").content(payload));}
        catch(Exception exception){throw new AssertionError(exception);}
    }
    private String webhook(long ignored,String reference,String code,boolean success){return signedWebhook(60000,reference,code,success,"link-1");}
    private String signedWebhook(long amount,String reference,String code,boolean success,String linkId){
        try{Map<String,Object> data=new java.util.LinkedHashMap<>();data.put("code",code);data.put("amount",amount);data.put("description","FH1");data.put("orderCode",123456789L);data.put("paymentLinkId",linkId);data.put("reference",reference);
            String canonical="amount="+amount+"&code="+code+"&description=FH1&orderCode=123456789&paymentLinkId="+linkId+"&reference="+reference;
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("test-checksum-key".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
            String signature=java.util.HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return json.writeValueAsString(Map.of("success",success,"data",data,"signature",signature));
        }catch(Exception exception){throw new AssertionError(exception);}
    }
    private void claims(String token,String uid){when(verifier.verify(token)).thenReturn(new FirebaseTokenVerifier.FirebaseClaims(uid,Map.of("email",uid+"@test.invalid","email_verified",true)));}
    private JsonNode read(MvcResult result){try{return json.readTree(result.getResponse().getContentAsString());}catch(Exception e){throw new AssertionError(e);}}
    private JsonNode fixture(String path){try(InputStream in=new ClassPathResource(path).getInputStream()){return json.readTree(in.readAllBytes());}catch(Exception e){throw new AssertionError(e);}}
}
