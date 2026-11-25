package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoodCatalogApiTests {
    private static final String TOKEN = "fixture-firebase-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper jsonMapper;
    @MockitoBean private FirebaseTokenVerifier tokenVerifier;

    private String firebaseUid;

    @BeforeEach
    void seedH2AndStubFirebase() {
        jdbc.update("DELETE FROM vouchers");
        jdbc.update("DELETE FROM dishes");
        jdbc.update("DELETE FROM menu_categories");
        jdbc.update("DELETE FROM stores");
        jdbc.update("DELETE FROM merchants");
        jdbc.update("DELETE FROM users");
        jdbc.update("""
                INSERT INTO users(id, firebase_uid, name, email, phone, address_line, role, avatar_url, status)
                VALUES (1, 'firebase-admin', 'Admin', 'admin@example.test', '', '', 'admin', '', 'active'),
                       (2, 'firebase-customer', 'Lan Nguyen', 'lan@example.test', '0900000000',
                        '1 Test Street', 'customer', 'https://img.example.test/lan.png', 'active'),
                       (3, 'firebase-seller', 'Minh', 'seller@example.test', '', '', 'seller', '', 'active')
                """);
        jdbc.update("INSERT INTO merchants(id, owner_user_id, brand_name) VALUES (1, 3, 'Bếp Xanh')");
        jdbc.update("""
                INSERT INTO stores(id, merchant_id, name, description, logo_url, cover_url,
                    address_line, latitude, longitude, phone, email, is_open, status, rating_avg,
                    avg_prep_minutes, open_time, close_time, deleted_at)
                VALUES (10, 1, 'Bếp Xanh', 'Món Việt', 'https://img.example.test/logo.png',
                    'https://img.example.test/cover.png', '1 Đường A', 10.0, 106.0, '0911111111',
                    'bep@example.test', TRUE, 'active', 4.5, 20, '09:00:00', '21:00:00', NULL)
                """);
        jdbc.update("INSERT INTO menu_categories(id, store_id, name, sort_order, is_active) VALUES (20, 10, 'Món chính', 0, TRUE)");
        jdbc.update("""
                INSERT INTO dishes(id, store_id, menu_category_id, platform_category_id, name,
                    description, image_url, price, is_available, rating_avg, rating_count, deleted_at)
                VALUES (100, 10, 20, NULL, 'Phở kiểm thử', 'Phở bò', 'https://img.example.test/pho.png',
                    45000, TRUE, 4.7, 8, NULL),
                       (101, 10, 20, NULL, 'Món ẩn', 'Không công khai', '', 1000, FALSE, 0, 0, NULL)
                """);
        jdbc.update("""
                INSERT INTO vouchers(id, code, title, description, image_url, discount_type,
                    discount_value, max_discount, min_order_amount, usage_limit, used_count,
                    starts_at, ends_at, is_active)
                VALUES (50, 'TEST10', 'Giảm 10%', 'Khuyến mãi kiểm thử',
                    'https://img.example.test/voucher.png', 'percentage', 10, 20000, 50000,
                    100, 2, NULL, NULL, TRUE),
                       (51, 'OFF', 'Đã tắt', '', '', 'fixed', 1000, NULL, 0,
                    NULL, 0, NULL, NULL, FALSE)
                """);
        firebaseUid = "firebase-customer";
        when(tokenVerifier.verify(TOKEN)).thenAnswer(invocation -> new FirebaseTokenVerifier.FirebaseClaims(
                firebaseUid, Map.of("email", "lan@example.test", "email_verified", true)));
    }

    @Test
    void profileJsonMatchesContractFixture() throws Exception {
        assertFixture("fixtures/user-profile.json", get("/api/users/me"));
    }

    @Test
    void firstProfileSyncRelinksVerifiedEmailAndDoesNotPromoteToAdmin() throws Exception {
        firebaseUid = "firebase-new-project-uid";
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/users/me").header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Lan Updated","phone":"0900000001","role":"admin"}
                                """))
                .andExpect(status().isOk()).andReturn();
        JsonNode actual = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("2", actual.at("/data/id").stringValue());
        assertEquals("firebase-new-project-uid", actual.at("/data/uid").stringValue());
        assertEquals("user", actual.at("/data/role").stringValue());
        assertEquals("ACTIVE", actual.at("/data/status").stringValue());
    }

    @Test
    void firstRegistrationDefaultsToCustomerAndPutPatchKeepProfileAliases() throws Exception {
        firebaseUid = "firebase-brand-new";
        when(tokenVerifier.verify(TOKEN)).thenReturn(new FirebaseTokenVerifier.FirebaseClaims(
                firebaseUid, Map.of("email", "new@example.test", "email_verified", true)));
        MvcResult created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/users/me").header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"name\":\"New Customer\",\"role\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals("user", jsonMapper.readTree(created.getResponse().getContentAsString())
                .at("/data/role").stringValue());

        firebaseUid = "firebase-customer";
        MvcResult updated = mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json").content("{\"fullName\":\"Lan Put\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals("Lan Put", jsonMapper.readTree(updated.getResponse().getContentAsString())
                .at("/data/name").stringValue());
        MvcResult patched = mockMvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json").content("{\"phoneNumber\":\"0900000011\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode profile = jsonMapper.readTree(patched.getResponse().getContentAsString()).at("/data");
        assertEquals("0900000011", profile.get("phone").stringValue());
        assertEquals("0900000011", profile.get("phoneNumber").stringValue());
    }

    @Test
    void categoriesJsonMatchesFixtureAndSupportsStoreFilter() throws Exception {
        assertFixture("fixtures/categories.json", get("/api/categories?restaurantId=10&limit=100&offset=0"));
    }

    @Test
    void storeListAndDetailJsonMatchContractFixtures() throws Exception {
        assertFixture("fixtures/stores.json", get("/api/stores?limit=100&offset=0"));
        assertFixture("fixtures/store-detail.json", get("/api/stores/10"));

        MvcResult located = mockMvc.perform(get("/api/stores/10?lat=10.0&lng=106.01")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andReturn();
        JsonNode location = jsonMapper.readTree(located.getResponse().getContentAsString()).at("/data");
        assertEquals(1.1, location.get("distanceKm").doubleValue(), 0.1);
        assertNotNull(location.get("shippingFee"));
    }

    @Test
    void dishListAndDetailJsonMatchFixturesAndHiddenDishRequiresOwnerOrAdmin() throws Exception {
        assertFixture("fixtures/dishes.json", get("/api/dishes").param("restaurantId", "10")
                .param("category", "Món chính"));
        assertFixture("fixtures/dish-detail.json", get("/api/dishes/100"));

        mockMvc.perform(get("/api/dishes?restaurantId=10&includeUnavailable=1")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
        firebaseUid = "firebase-seller";
        mockMvc.perform(get("/api/dishes?restaurantId=10&includeUnavailable=1")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
        firebaseUid = "firebase-customer";
        mockMvc.perform(get("/api/dishes/101").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void promotionsAndVouchersAliasReturnOnlyEffectivePromotionsForCustomer() throws Exception {
        assertFixture("fixtures/vouchers.json", get("/api/vouchers"));
        firebaseUid = "firebase-admin";
        MvcResult adminResult = mockMvc.perform(get("/api/promotions").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andReturn();
        JsonNode adminJson = jsonMapper.readTree(adminResult.getResponse().getContentAsString());
        assertEquals(2, adminJson.get("data").size());
    }

    @Test
    void catalogGetsProvidePrivateCacheAndEtagWithConditional304() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andReturn();
        String etag = first.getResponse().getHeader("ETag");
        assertNotNull(etag);
        String body = first.getResponse().getContentAsString();
        String expectedEtag = "\"" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))) + "\"";
        assertEquals(expectedEtag, etag);
        assertEquals("private, max-age=60, stale-while-revalidate=120",
                first.getResponse().getHeader("Cache-Control"));
        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + TOKEN)
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    private void assertFixture(String fixtureName,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andReturn();
        JsonNode actual = jsonMapper.readTree(result.getResponse().getContentAsString());
        try (InputStream input = new ClassPathResource(fixtureName).getInputStream()) {
            JsonNode expected = jsonMapper.readTree(input);
            assertEquals(expected, actual, "Response JSON differs from " + fixtureName);
        }
    }
}
