package com.example.backend.management;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.auth.FirebaseTokenVerifier;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ManagementApiTests {
    private static final String ADMIN="admin-token",SELLER="seller-token",OTHER_SELLER="other-seller-token",CUSTOMER="customer-token",NEW_SELLER="new-seller-token";
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired JsonMapper json;
    @Autowired UserManagementService userManagement;
    @MockitoBean FirebaseTokenVerifier verifier;

    @BeforeEach void seed(){
        for(String table:new String[]{"messages","conversation_participants","conversations","activity_logs","vouchers","dishes","menu_categories","store_staff","stores","merchants","users"})jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO users(id,firebase_uid,name,email,phone,address_line,role,status) VALUES(1,'uid-admin','Admin','admin@test.invalid','','','admin','active'),(2,'uid-customer','Customer','customer@test.invalid','0900000000','Address','customer','active'),(3,'uid-seller','Seller','seller@test.invalid','','','seller','active'),(4,'uid-other-seller','Other seller','other-seller@test.invalid','','','seller','active'),(5,'uid-new-seller','New seller','new-seller@test.invalid','','','seller','active')");
        jdbc.update("INSERT INTO merchants(id,owner_user_id,brand_name) VALUES(10,3,'Bếp Xanh'),(20,4,'Quán Khác')");
        jdbc.update("""
                INSERT INTO stores(id,merchant_id,name,description,logo_url,cover_url,address_line,latitude,longitude,phone,email,is_open,status,rating_avg,avg_prep_minutes,open_time,close_time,order_notify,sound_notify,push_notify,email_notify,promo_notify)
                VALUES(10,10,'Bếp Xanh','Món Việt','https://img.example.test/logo.png','https://img.example.test/cover.png','1 Đường A',10.0,106.0,'0911111111','bep@example.test',TRUE,'active',4.5,20,'09:00:00','21:00:00',TRUE,TRUE,TRUE,FALSE,TRUE),
                      (20,20,'Quán Khác','','','','',NULL,NULL,NULL,NULL,TRUE,'active',0,30,NULL,NULL,TRUE,TRUE,TRUE,FALSE,TRUE)""");
        jdbc.update("INSERT INTO store_staff(store_id,user_id,staff_role) VALUES(10,3,'owner'),(20,4,'owner')");
        jdbc.update("INSERT INTO menu_categories(id,store_id,name) VALUES(30,10,'Món chính'),(40,20,'Món chính')");
        jdbc.update("INSERT INTO dishes(id,store_id,menu_category_id,name,description,image_url,price,is_available) VALUES(100,10,30,'Phở','Món thử','https://img.example.test/pho.png',45000,TRUE),(200,20,40,'Bún','Món thử','',30000,TRUE)");
        when(verifier.verify(ADMIN)).thenReturn(claims("uid-admin"));when(verifier.verify(SELLER)).thenReturn(claims("uid-seller"));
        when(verifier.verify(OTHER_SELLER)).thenReturn(claims("uid-other-seller"));when(verifier.verify(CUSTOMER)).thenReturn(claims("uid-customer"));
        when(verifier.verify(NEW_SELLER)).thenReturn(claims("uid-new-seller"));
    }

    @Test void sellerMineMatchesFixtureAndCanCreateThenUpdateOwnStore() throws Exception {
        assertFixture("fixtures/store-mine.json",get("/api/stores/mine").header("Authorization","Bearer "+SELLER));
        MvcResult created=mvc.perform(post("/api/stores/mine").header("Authorization","Bearer "+NEW_SELLER)
                        .contentType("application/json").content("{\"name\":\"Quán mới\",\"address\":\"Đường B\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals("Quán mới",json.readTree(created.getResponse().getContentAsString()).at("/data/name").stringValue());
        long newStore=jdbc.queryForObject("SELECT id FROM stores WHERE name='Quán mới'",Long.class);
        mvc.perform(patch("/api/stores/mine").header("Authorization","Bearer "+NEW_SELLER)
                        .contentType("application/json").content("{\"isOpen\":false,\"phone\":\"0901234567\"}"))
                .andExpect(status().isOk());
        assertFalse(jdbc.queryForObject("SELECT is_open FROM stores WHERE id=?",Boolean.class,newStore));
        assertEquals("0901234567",jdbc.queryForObject("SELECT phone FROM stores WHERE id=?",String.class,newStore));
    }

    @Test void sellerCannotWriteAnotherStoresDishAndNegativePriceIsRejected() throws Exception {
        String payload="{\"restaurantId\":10,\"name\":\"Món mới\",\"price\":10000,\"category\":\"Món chính\"}";
        mvc.perform(post("/api/dishes").header("Authorization","Bearer "+OTHER_SELLER).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/dishes/100").header("Authorization","Bearer "+OTHER_SELLER).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/dishes/100").header("Authorization","Bearer "+OTHER_SELLER)).andExpect(status().isForbidden());
        mvc.perform(post("/api/dishes").header("Authorization","Bearer "+SELLER).contentType("application/json")
                .content("{\"restaurantId\":10,\"name\":\"Giá sai\",\"price\":-1}" )).andExpect(status().isUnprocessableEntity());
        MvcResult created=mvc.perform(post("/api/dishes").header("Authorization","Bearer "+SELLER).contentType("application/json")
                .content("{\"restaurantId\":10,\"name\":\"Món mới\",\"price\":12000,\"category\":\"Món chính\",\"available\":true}" )).andExpect(status().isCreated()).andReturn();
        String dishId=read(created).get("id").stringValue();
        mvc.perform(put("/api/dishes/"+dishId).header("Authorization","Bearer "+SELLER).contentType("application/json")
                .content("{\"restaurantId\":10,\"name\":\"Món cập nhật\",\"price\":15000,\"category\":\"Món chính\"}" )).andExpect(status().isOk());
        assertEquals("Món cập nhật",jdbc.queryForObject("SELECT name FROM dishes WHERE id=?",String.class,Long.valueOf(dishId)));
    }

    @Test void softDeletedDishIsOmittedFromCatalog() throws Exception {
        mvc.perform(delete("/api/dishes/100").header("Authorization","Bearer "+SELLER)).andExpect(status().isOk());
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM dishes WHERE id=100",java.sql.Timestamp.class));
        JsonNode data=read(mvc.perform(get("/api/dishes?restaurantId=10").header("Authorization","Bearer "+SELLER)).andExpect(status().isOk()).andReturn()).get("data");
        assertEquals(0,data.size());
    }

    @Test void promotionsAreAdminOnlyAndValidatedAndCanBeManagedByBothAliases() throws Exception {
        String invalid="{\"code\":\"BAD\",\"title\":\"Sai\",\"type\":\"percentage\",\"value\":101}";
        mvc.perform(post("/api/vouchers").header("Authorization","Bearer "+ADMIN).contentType("application/json").content(invalid)).andExpect(status().isUnprocessableEntity());
        String valid="{\"storeId\":10,\"code\":\"TEST20\",\"title\":\"Giảm 20%\",\"type\":\"percentage\",\"value\":20,\"minOrderValue\":50000,\"usageLimit\":20,\"startDate\":\"2026-11-01T00:00:00\",\"endDate\":\"2026-12-01T00:00:00\",\"status\":\"active\"}";
        MvcResult created=mvc.perform(post("/api/promotions").header("Authorization","Bearer "+ADMIN).contentType("application/json").content(valid)).andExpect(status().isCreated()).andReturn();
        String id=read(created).get("id").stringValue();
        mvc.perform(patch("/api/vouchers/"+id).header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"status\":\"inactive\"}" )).andExpect(status().isOk());
        assertFalse(jdbc.queryForObject("SELECT is_active FROM vouchers WHERE id=?",Boolean.class,Long.valueOf(id)));
        mvc.perform(delete("/api/promotions/"+id).header("Authorization","Bearer "+ADMIN)).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM vouchers WHERE id=?",Integer.class,Long.valueOf(id)));
        mvc.perform(post("/api/vouchers").header("Authorization","Bearer "+SELLER).contentType("application/json").content(valid)).andExpect(status().isForbidden());
        mvc.perform(post("/api/vouchers").header("Authorization","Bearer "+ADMIN).contentType("application/json")
                .content("{\"code\":\"DATES\",\"title\":\"Sai hạn\",\"type\":\"fixed\",\"value\":1000,\"startDate\":\"2026-12-02T00:00:00\",\"endDate\":\"2026-12-01T00:00:00\"}" )).andExpect(status().isUnprocessableEntity());
    }

    @Test void usersRequireAdminExceptForSharedConversationAndAdminGuardIsApplied() throws Exception {
        mvc.perform(get("/api/users").header("Authorization","Bearer "+SELLER)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization","Bearer "+ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/api/users/uid-customer").header("Authorization","Bearer "+SELLER)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO conversations(id,type,created_by) VALUES(1,'customer_store',2)");
        jdbc.update("INSERT INTO conversation_participants(conversation_id,user_id) VALUES(1,2),(1,3)");
        JsonNode profile=read(mvc.perform(get("/api/users/uid-customer").header("Authorization","Bearer "+SELLER)).andExpect(status().isOk()).andReturn());
        try(InputStream in=new ClassPathResource("fixtures/admin-user-view.json").getInputStream()){assertEquals(json.readTree(in),profile);}
        mvc.perform(patch("/api/users/uid-admin").header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"role\":\"seller\"}" )).andExpect(status().isConflict());
        mvc.perform(patch("/api/users/uid-customer").header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"role\":\"root\"}" )).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/users/uid-customer").header("Authorization","Bearer "+SELLER)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/uid-customer").header("Authorization","Bearer "+ADMIN)).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE firebase_uid='uid-customer' AND deleted_at IS NULL",Integer.class));
        mvc.perform(get("/api/users/uid-customer").header("Authorization","Bearer "+ADMIN)).andExpect(status().isNotFound());
        JsonNode users=read(mvc.perform(get("/api/users").header("Authorization","Bearer "+ADMIN)).andExpect(status().isOk()).andReturn()).get("data");
        assertTrue(java.util.stream.StreamSupport.stream(users.spliterator(),false).noneMatch(u->"uid-customer".equals(u.path("uid").asText())));
    }

    @Test void adminCannotDeleteLockOrDemoteSelfAndSystemCannotLoseItsLastAdmin() throws Exception {
        mvc.perform(patch("/api/users/uid-admin").header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"role\":\"seller\"}" )).andExpect(status().isConflict());
        mvc.perform(patch("/api/users/uid-admin").header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"isAvailable\":false}" )).andExpect(status().isConflict());
        mvc.perform(delete("/api/users/uid-admin").header("Authorization","Bearer "+ADMIN)).andExpect(status().isConflict());
        var fakeAdmin=new com.example.backend.auth.FirebasePrincipal("stale-admin",99L,"admin","admin",null,false);
        var demote=new com.example.backend.management.ManagementDtos.UserPatch(null,null,null,null,null,"seller",null);
        var failure=assertThrows(com.example.backend.api.ApiException.class,()->userManagement.patch(fakeAdmin,"uid-admin",demote));
        assertEquals(HttpStatus.CONFLICT,failure.getStatus());
        var deleteLast=assertThrows(com.example.backend.api.ApiException.class,()->userManagement.delete(fakeAdmin,"uid-admin"));
        assertEquals(HttpStatus.CONFLICT,deleteLast.getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='admin' AND status='active' AND deleted_at IS NULL",Integer.class));
    }

    @Test void adminRolePatchAcceptsContractAliasesAndRejectsUnknownRole() throws Exception {
        mvc.perform(patch("/api/users/uid-customer").header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"role\":\"restaurant\"}" )).andExpect(status().isOk());
        assertEquals("seller",jdbc.queryForObject("SELECT role FROM users WHERE firebase_uid='uid-customer'",String.class));
        mvc.perform(patch("/api/users/uid-customer").header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"role\":\"shipper\"}" )).andExpect(status().isOk());
        assertEquals("driver",jdbc.queryForObject("SELECT role FROM users WHERE firebase_uid='uid-customer'",String.class));
        mvc.perform(patch("/api/users/uid-customer").header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"role\":\"user\"}" )).andExpect(status().isOk());
        assertEquals("customer",jdbc.queryForObject("SELECT role FROM users WHERE firebase_uid='uid-customer'",String.class));
        mvc.perform(patch("/api/users/uid-customer").header("Authorization","Bearer "+ADMIN).contentType("application/json").content("{\"role\":\"root\"}" )).andExpect(status().isUnprocessableEntity());
    }

    @Test void deletingUsedVoucherDeactivatesItAndReturnsSameOkShape() throws Exception {
        jdbc.update("INSERT INTO vouchers(id,code,title,discount_type,discount_value,used_count,is_active) VALUES(60,'USED_LINK','Used via order','fixed',1000,0,TRUE),(61,'USED_COUNT','Used count','fixed',1000,3,TRUE),(62,'UNUSED','Not used','fixed',1000,0,TRUE)");
        jdbc.update("INSERT INTO order_vouchers(order_id,voucher_id,discount_amount) VALUES(900,60,1000)");
        JsonNode linked=read(mvc.perform(delete("/api/vouchers/60").header("Authorization","Bearer "+ADMIN)).andExpect(status().isOk()).andReturn());
        JsonNode counted=read(mvc.perform(delete("/api/promotions/61").header("Authorization","Bearer "+ADMIN)).andExpect(status().isOk()).andReturn());
        assertEquals(json.readTree("{\"ok\":true}"),linked);assertEquals(linked,counted);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM vouchers WHERE id=60 AND is_active=FALSE",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM vouchers WHERE id=61 AND is_active=FALSE",Integer.class));
        mvc.perform(delete("/api/vouchers/62").header("Authorization","Bearer "+ADMIN)).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM vouchers WHERE id=62",Integer.class));
    }

    private FirebaseTokenVerifier.FirebaseClaims claims(String uid){return new FirebaseTokenVerifier.FirebaseClaims(uid,Map.of("email",uid+"@test.invalid","email_verified",true));}
    private JsonNode read(MvcResult result){try{return json.readTree(result.getResponse().getContentAsString());}catch(Exception e){throw new AssertionError(e);}}
    private void assertFixture(String name,org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)throws Exception{JsonNode actual=read(mvc.perform(req.header("Authorization","Bearer "+SELLER)).andExpect(status().isOk()).andReturn());try(InputStream in=new ClassPathResource(name).getInputStream()){assertEquals(json.readTree(in),actual);}}
}
