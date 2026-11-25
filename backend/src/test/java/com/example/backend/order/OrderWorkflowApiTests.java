package com.example.backend.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.auth.FirebaseTokenVerifier;
import com.example.backend.notification.FcmClient;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:foodhubordertest;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderWorkflowApiTests {
    private static final String CUSTOMER="customer-token", OTHER_CUSTOMER="other-customer-token";
    private static final String SELLER="seller-token", OTHER_SELLER="other-seller-token";
    private static final String DRIVER_A="driver-a-token", DRIVER_B="driver-b-token", ADMIN="admin-token";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @MockitoBean private FirebaseTokenVerifier verifier;
    @MockitoBean private FcmClient fcmClient;

    @BeforeEach void seed(){
        jdbc.update("DELETE FROM notifications"); jdbc.update("DELETE FROM order_vouchers");
        jdbc.update("DELETE FROM order_status_logs"); jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM payments"); jdbc.update("DELETE FROM deliveries"); jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM drivers"); jdbc.update("DELETE FROM vouchers"); jdbc.update("DELETE FROM dishes");
        jdbc.update("DELETE FROM menu_categories"); jdbc.update("DELETE FROM stores"); jdbc.update("DELETE FROM merchants"); jdbc.update("DELETE FROM users");
        for(String table:new String[]{"orders","order_items","payments","deliveries","order_status_logs","notifications","drivers"})
            jdbc.execute("ALTER TABLE "+table+" ALTER COLUMN id RESTART WITH 1");
        jdbc.update("""
            INSERT INTO users(id,firebase_uid,name,email,phone,role,status) VALUES
            (1,'uid-customer','Lan','lan@test.invalid','0901','customer','active'),
            (2,'uid-other-customer','Mai','mai@test.invalid','0902','customer','active'),
            (3,'uid-seller','Seller','seller@test.invalid','0903','seller','active'),
            (4,'uid-other-seller','Seller 2','seller2@test.invalid','0904','seller','active'),
            (5,'uid-driver-a','Driver A','drivera@test.invalid','','driver','active'),
            (6,'uid-driver-b','Driver B','driverb@test.invalid','','driver','active'),
            (7,'uid-admin','Admin','admin@test.invalid','','admin','active')
            """);
        jdbc.update("INSERT INTO merchants(id,owner_user_id,brand_name) VALUES(10,3,'Store A'),(11,4,'Store B')");
        jdbc.update("""
            INSERT INTO stores(id,merchant_id,name,latitude,longitude,is_open,status,deleted_at)
            VALUES(10,10,'Store A',10.0,106.0,TRUE,'active',NULL),(11,11,'Store B',10.0,106.0,FALSE,'active',NULL)
            """);
        jdbc.update("""
            INSERT INTO dishes(id,store_id,name,price,is_available,deleted_at) VALUES
            (100,10,'Pho',45000,TRUE,NULL),(101,10,'Unavailable',12000,FALSE,NULL),(102,11,'Store B dish',30000,TRUE,NULL)
            """);
        jdbc.update("""
            INSERT INTO vouchers(id,store_id,code,title,discount_type,discount_value,max_discount,min_order_amount,usage_limit,used_count,starts_at,ends_at,is_active)
            VALUES(50,NULL,'PCT','Percent','percentage',50,10000,0,100,0,NULL,NULL,TRUE),
                  (51,NULL,'FIX','Fixed','fixed',200000,NULL,0,100,0,NULL,NULL,TRUE),
                  (52,NULL,'SHIP','Ship','free_shipping',0,NULL,0,100,0,NULL,NULL,TRUE),
                  (53,NULL,'OLD','Expired','fixed',1000,NULL,0,100,0,NULL,CURRENT_TIMESTAMP - INTERVAL '1' DAY,TRUE),
                  (54,NULL,'USED','Used','fixed',1000,NULL,0,1,1,NULL,NULL,TRUE),
                  (55,11,'OTHER','Other store','fixed',1000,NULL,0,10,0,NULL,NULL,TRUE),
                  (56,NULL,'MIN','Minimum','fixed',1000,NULL,100000,10,0,NULL,NULL,TRUE)
            """);
        token(CUSTOMER,"uid-customer","customer"); token(OTHER_CUSTOMER,"uid-other-customer","customer");
        token(SELLER,"uid-seller","seller"); token(OTHER_SELLER,"uid-other-seller","seller");
        token(DRIVER_A,"uid-driver-a","driver"); token(DRIVER_B,"uid-driver-b","driver"); token(ADMIN,"uid-admin","admin");
    }

    @Test void createUsesDatabasePriceClampsQuantityAndMatchesJsonFixture() throws Exception {
        MvcResult result=postOrder(CUSTOMER,"""
                {"restaurantId":10,"items":[{"dishId":100,"quantity":0,"price":1}]}
                """).andExpect(status().isCreated()).andReturn();
        JsonNode actual=json.readTree(result.getResponse().getContentAsString());
        String id=actual.get("id").stringValue();
        JsonNode expected=fixture("fixtures/order-created-cash.json",id);
        assertEquals(expected,actual);
        assertEquals(45000,new BigDecimal(actual.at("/data/totalPrice").toString()).subtract(BigDecimal.valueOf(15000)).intValue());
        assertEquals(45000,jdbc.queryForObject("SELECT unit_price FROM order_items WHERE order_id=?",BigDecimal.class,Long.valueOf(id)).intValue());
        assertEquals(1,jdbc.queryForObject("SELECT quantity FROM order_items WHERE order_id=?",Integer.class,Long.valueOf(id)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id=?",Integer.class,Long.valueOf(id)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM deliveries WHERE order_id=?",Integer.class,Long.valueOf(id)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_status_logs WHERE order_id=?",Integer.class,Long.valueOf(id)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE related_order_id=?",Integer.class,Long.valueOf(id)));
    }

    @Test void quantityAboveMaximumIsClampedTo99AndPayosStartsPendingPayment() throws Exception {
        MvcResult result=postOrder(CUSTOMER,"""
                {"restaurantId":10,"paymentMethod":"payos","items":[{"dishId":100,"quantity":120}]}
                """).andExpect(status().isCreated()).andReturn();
        long id=Long.parseLong(json.readTree(result.getResponse().getContentAsString()).get("id").stringValue());
        assertEquals(99,jdbc.queryForObject("SELECT quantity FROM order_items WHERE order_id=?",Integer.class,id));
        assertEquals("pending_payment",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,id));
        assertEquals("payos",jdbc.queryForObject("SELECT provider FROM payments WHERE order_id=?",String.class,id));
        assertEquals("pending",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,id));
    }

    @Test void shippingFeeUsesBaseThroughThreeKmExtraPerRoundedUpKmAndBaseWithoutCoordinates() throws Exception {
        long within=createOrder(CUSTOMER,"{\"restaurantId\":10,\"customerLat\":10.0,\"customerLng\":106.02735,\"items\":[{\"dishId\":100,\"quantity\":1}]}");
        long beyond=createOrder(CUSTOMER,"{\"restaurantId\":10,\"customerLat\":10.0,\"customerLng\":106.04,\"items\":[{\"dishId\":100,\"quantity\":1}]}");
        long noCoordinates=createOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":100,\"quantity\":1}]}");
        assertEquals(15000,jdbc.queryForObject("SELECT delivery_fee FROM orders WHERE id=?",BigDecimal.class,within).intValue());
        assertEquals(35000,jdbc.queryForObject("SELECT delivery_fee FROM orders WHERE id=?",BigDecimal.class,beyond).intValue());
        assertEquals(15000,jdbc.queryForObject("SELECT delivery_fee FROM orders WHERE id=?",BigDecimal.class,noCoordinates).intValue());
    }

    @Test void supportsVoucherTypesCapsDiscountAndRejectsInvalidVouchers() throws Exception {
        long percent=createOrder(CUSTOMER,"{\"restaurantId\":10,\"appliedPromotionId\":50,\"items\":[{\"dishId\":100}]}");
        long fixed=createOrder(CUSTOMER,"{\"restaurantId\":10,\"appliedPromotionId\":51,\"items\":[{\"dishId\":100}]}");
        long freeShip=createOrder(CUSTOMER,"{\"restaurantId\":10,\"appliedPromotionId\":52,\"items\":[{\"dishId\":100}]}");
        assertEquals(10000,jdbc.queryForObject("SELECT discount_amount FROM orders WHERE id=?",BigDecimal.class,percent).intValue());
        assertEquals(0,jdbc.queryForObject("SELECT total_amount FROM orders WHERE id=?",BigDecimal.class,fixed).intValue());
        assertEquals(45000,jdbc.queryForObject("SELECT total_amount FROM orders WHERE id=?",BigDecimal.class,freeShip).intValue());
        assertEquals(1,jdbc.queryForObject("SELECT used_count FROM vouchers WHERE id=50",Integer.class));
        for(String voucherId:new String[]{"53","54","55","56"}){
            String body="{\"restaurantId\":10,\"appliedPromotionId\":"+voucherId+",\"items\":[{\"dishId\":100}]}";
            postOrder(CUSTOMER,body).andExpect(status().isUnprocessableEntity());
        }
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));
    }

    @Test void rejectsWrongStoreUnavailableDishAndClosedStore() throws Exception {
        postOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":102}]}").andExpect(status().isUnprocessableEntity());
        postOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":101}]}").andExpect(status().isUnprocessableEntity());
        postOrder(CUSTOMER,"{\"restaurantId\":11,\"items\":[{\"dishId\":102}]}").andExpect(status().isUnprocessableEntity());
        postOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[]}").andExpect(status().isUnprocessableEntity());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));
    }

    @Test void databaseFailureAfterOrderItemsRollsBackAllOrderRows() throws Exception {
        postOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":100,\"quantity\":2}]}")
                .andExpect(status().isServiceUnavailable());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_items",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM payments",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM deliveries",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_status_logs",Integer.class));
    }

    @Test void roleScopedOrderListsDoNotLeakOtherCustomersStoresOrDriversOrders() throws Exception {
        long own=createOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":100}]}");
        long other=createOrder(OTHER_CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":100}]}");
        MvcResult customer=mvc.perform(get("/api/orders").header("Authorization","Bearer "+CUSTOMER)).andExpect(status().isOk()).andReturn();
        JsonNode customerData=json.readTree(customer.getResponse().getContentAsString()).get("data");
        assertEquals(1,customerData.size()); assertEquals(Long.toString(own),customerData.get(0).get("id").stringValue());
        assertEquals("PENDING",customerData.get(0).get("status").stringValue());
        assertEquals("uid-customer",customerData.get(0).get("userId").stringValue());
        assertTrue(customerData.get(0).get("items").get(0).get("dishId").isString());
        jdbc.update("UPDATE orders SET status='accepted' WHERE id IN (?,?)",own,other);
        jdbc.update("INSERT INTO drivers(id,user_id,work_status) VALUES(1,5,'busy'),(2,6,'busy')");
        jdbc.update("UPDATE deliveries SET driver_id=1,status='assigned' WHERE order_id=?",own);
        jdbc.update("UPDATE deliveries SET driver_id=2,status='assigned' WHERE order_id=?",other);
        JsonNode driverOrders=json.readTree(mvc.perform(get("/api/orders").header("Authorization","Bearer "+DRIVER_A))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertEquals(1,driverOrders.size()); assertEquals(Long.toString(own),driverOrders.get(0).get("id").stringValue());
        jdbc.update("UPDATE stores SET is_open=TRUE WHERE id=11");
        long storeB=createOrder(OTHER_CUSTOMER,"{\"restaurantId\":11,\"items\":[{\"dishId\":102}]}");
        JsonNode sellerOrders=json.readTree(mvc.perform(get("/api/orders").header("Authorization","Bearer "+SELLER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertTrue(sellerOrders.size()>=2);
        assertTrue(java.util.stream.StreamSupport.stream(sellerOrders.spliterator(),false)
                .anyMatch(row->Long.toString(own).equals(row.get("id").stringValue())));
        assertFalse(java.util.stream.StreamSupport.stream(sellerOrders.spliterator(),false)
                .anyMatch(row->Long.toString(storeB).equals(row.get("id").stringValue())));
    }

    @Test void concurrentAcceptHasSingleWinnerAndUnavailableDriverGetsConflict() throws Exception {
        jdbc.update("INSERT INTO drivers(id,user_id,work_status) VALUES(1,5,'available'),(2,6,'available')");
        long id=insertAcceptedOrder();
        var pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
            Callable<Integer> a=()->{ready.countDown();start.await();return mvc.perform(post("/api/deliveries/"+id+"/accept").header("Authorization","Bearer "+DRIVER_A)).andReturn().getResponse().getStatus();};
            Callable<Integer> b=()->{ready.countDown();start.await();return mvc.perform(post("/api/deliveries/"+id+"/accept").header("Authorization","Bearer "+DRIVER_B)).andReturn().getResponse().getStatus();};
            var futures=java.util.List.of(pool.submit(a),pool.submit(b));ready.await();start.countDown();
            int first=futures.get(0).get(),second=futures.get(1).get();
            assertEquals(1,(first==200?1:0)+(second==200?1:0));
            assertTrue(first==409||second==409,"losing request should conflict");
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM deliveries WHERE order_id=? AND driver_id IS NOT NULL",Integer.class,id));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM drivers WHERE work_status='busy'",Integer.class));
        } finally {pool.shutdownNow();}
        long another=insertAcceptedOrder();
        jdbc.update("UPDATE drivers SET work_status='offline' WHERE user_id=5");
        mvc.perform(post("/api/deliveries/"+another+"/accept").header("Authorization","Bearer "+DRIVER_A))
                .andExpect(status().isConflict());
    }

    @Test void stateMachineRejectsIllegalTransitionsAndCompletionCounterIsIdempotent() throws Exception {
        long id=createOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":100}]}");
        mvc.perform(patch("/api/orders/"+id).header("Authorization","Bearer "+SELLER).contentType("application/json")
                .content("{\"status\":\"shipping\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/orders/"+id).header("Authorization","Bearer "+OTHER_SELLER).contentType("application/json")
                .content("{\"status\":\"accepted\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/"+id).header("Authorization","Bearer "+CUSTOMER).contentType("application/json")
                .content("{\"status\":\"completed\"}")).andExpect(status().isConflict());
        jdbc.update("UPDATE orders SET status='shipping' WHERE id=?",id);
        jdbc.update("INSERT INTO drivers(id,user_id,work_status) VALUES(1,5,'busy')");
        jdbc.update("UPDATE deliveries SET driver_id=1,status='picked_up' WHERE order_id=?",id);
        mvc.perform(patch("/api/orders/"+id).header("Authorization","Bearer "+DRIVER_A).contentType("application/json")
                .content("{\"status\":\"completed\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/orders/"+id).header("Authorization","Bearer "+DRIVER_A).contentType("application/json")
                .content("{\"status\":\"completed\"}")).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT completed_trips FROM drivers WHERE id=1",Integer.class));
    }

    @Test void invalidStatusReturns422AndCustomerCanCancelOwnPendingOrder() throws Exception {
        long id=createOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":100}]}");
        mvc.perform(patch("/api/orders/"+id).header("Authorization","Bearer "+CUSTOMER).contentType("application/json")
                .content("{\"status\":\"unknown\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(patch("/api/orders/"+id).header("Authorization","Bearer "+CUSTOMER).contentType("application/json")
                .content("{\"status\":\"cancelled\",\"cancelReason\":\"mistake\"}")).andExpect(status().isOk());
        assertEquals("cancelled",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,id));
    }

    @Test void stateMachineAllowsOnlyTheDocumentedTransitionEdges() throws Exception {
        String[][] edges={
            {"pending_payment","pending","yes"},{"pending_payment","cancelled","yes"},
            {"pending","accepted","yes"},{"pending","cancelled","yes"},
            {"accepted","processing","yes"},{"accepted","shipping","yes"},{"accepted","cancelled","yes"},
            {"processing","shipping","yes"},{"processing","cancelled","yes"},{"shipping","completed","yes"},
            {"pending_payment","accepted","no"},{"pending","processing","no"},
            {"processing","completed","no"},{"shipping","accepted","no"},
            {"completed","cancelled","no"},{"cancelled","pending","no"}
        };
        for(String[] edge:edges){
            long id=createOrder(CUSTOMER,"{\"restaurantId\":10,\"items\":[{\"dishId\":100}]}");
            jdbc.update("UPDATE orders SET status=? WHERE id=?",edge[0],id);
            var result=mvc.perform(patch("/api/orders/"+id).header("Authorization","Bearer "+ADMIN)
                    .contentType("application/json").content("{\"status\":\""+edge[1]+"\"}")).andReturn();
            assertEquals("yes".equals(edge[2])?200:409,result.getResponse().getStatus(),edge[0]+" -> "+edge[1]);
        }
    }

    @Test void postAddressStoresAddressAndReturnsStringId() throws Exception {
        MvcResult result=mvc.perform(post("/api/addresses").header("Authorization","Bearer "+CUSTOMER)
                .contentType("application/json").content("""
                    {"label":"Nhà","receiverName":"Lan","receiverPhone":"0901","addressDetail":"1 Main St","city":"HCMC","isDefault":true}
                    """))
                .andExpect(status().isOk()).andReturn();
        JsonNode response=json.readTree(result.getResponse().getContentAsString());
        assertTrue(response.get("id").isString());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_addresses WHERE user_id=1 AND receiver_name='Lan'",Integer.class));
    }

    private ResultActions postOrder(String token,String body)throws Exception{
        return mvc.perform(post("/api/orders").header("Authorization","Bearer "+token).contentType("application/json").content(body));
    }
    private long createOrder(String token,String body)throws Exception{
        MvcResult result=postOrder(token,body).andExpect(status().isCreated()).andReturn();
        return Long.parseLong(json.readTree(result.getResponse().getContentAsString()).get("id").stringValue());
    }
    private long insertAcceptedOrder(){
        jdbc.update("INSERT INTO orders(code,customer_id,store_id,status,receiver_name,receiver_phone,delivery_address,subtotal,delivery_fee,discount_amount,total_amount,payment_method,payment_status) VALUES(?,1,10,'accepted','Lan','0901','Test',45000,15000,0,60000,'cash','pending')","ACCEPT-"+System.nanoTime());
        long id=jdbc.queryForObject("SELECT MAX(id) FROM orders",Long.class);jdbc.update("INSERT INTO deliveries(order_id,status,delivery_fee) VALUES(?,'unassigned',15000)",id);return id;
    }
    private void token(String token,String uid,String role){when(verifier.verify(token)).thenReturn(new FirebaseTokenVerifier.FirebaseClaims(uid,Map.of("email",uid+"@test.invalid","email_verified",true)));}
    private JsonNode fixture(String name,String id)throws Exception{try(InputStream in=new ClassPathResource(name).getInputStream()){return json.readTree(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("__ORDER_ID__",id));}}
}
