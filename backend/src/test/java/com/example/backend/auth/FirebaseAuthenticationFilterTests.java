package com.example.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.BackendApplication;
import com.example.backend.user.UserAccount;
import com.example.backend.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = BackendApplication.class)
@ActiveProfiles("test")
class FirebaseAuthenticationFilterTests {
    private static final String PROJECT_ID = "foodhub-test-project";
    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");

    @Autowired private UserAccountRepository userRepository;
    @Autowired private JsonMapper jsonMapper;

    private KeyPair keyPair;
    private FirebaseAuthenticationFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        FirebasePublicCertificateProvider provider = forceRefresh -> Map.of("test-kid", keyPair.getPublic());
        FirebaseTokenVerifier verifier = new FirebaseTokenVerifier(provider, jsonMapper, PROJECT_ID,
                Clock.fixed(NOW, ZoneOffset.UTC));
        filter = new FirebaseAuthenticationFilter(verifier, userRepository, jsonMapper);
    }

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void verifiesSignedTokenAndLoadsActiveUserAndMappedRoleFromH2() throws Exception {
        userRepository.save(new UserAccount(41L, "firebase-customer", "customer", "active", null));
        MockHttpServletRequest request = authorizedRequest("GET", "/api/orders", token("firebase-customer", NOW.getEpochSecond() + 3600, NOW.getEpochSecond()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> continued.set(true));

        assertTrue(continued.get());
        assertEquals(200, response.getStatus());
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        FirebasePrincipal principal = assertInstanceOf(FirebasePrincipal.class, authentication.getPrincipal());
        assertEquals(41L, principal.userId());
        assertEquals("customer", principal.databaseRole());
        assertEquals("user", principal.androidRole());
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void rejectsSuspendedAndSoftDeletedUsers() throws Exception {
        userRepository.save(new UserAccount(42L, "firebase-suspended", "customer", "suspended", null));
        MockHttpServletResponse suspended = invoke("GET", "/api/orders", "firebase-suspended");
        assertEquals(403, suspended.getStatus());
        assertTrue(suspended.getContentAsString().contains("\"error\""));

        userRepository.save(new UserAccount(43L, "firebase-deleted", "customer", "active", NOW.atOffset(ZoneOffset.UTC).toLocalDateTime()));
        MockHttpServletResponse deleted = invoke("GET", "/api/orders", "firebase-deleted");
        assertEquals(403, deleted.getStatus());
    }

    @Test
    void allowsValidFirebaseIdentityToReachProfileCreationWithoutExistingDbUser() throws Exception {
        MockHttpServletRequest request = authorizedRequest("POST", "/api/users",
                token("firebase-new-user", NOW.getEpochSecond() + 3600, NOW.getEpochSecond()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> continued.set(true));

        assertTrue(continued.get());
        FirebasePrincipal principal = assertInstanceOf(FirebasePrincipal.class,
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication().getPrincipal());
        assertEquals("firebase-new-user", principal.firebaseUid());
        assertEquals(null, principal.databaseRole());
        assertFalse(userRepository.existsById(99L));
    }

    @Test
    void rejectsUnknownProfileOnProtectedRouteAndExpiredToken() throws Exception {
        MockHttpServletResponse missingUser = invoke("GET", "/api/orders", "firebase-without-profile");
        assertEquals(404, missingUser.getStatus());

        MockHttpServletRequest request = authorizedRequest("GET", "/api/orders",
                token("firebase-user", NOW.getEpochSecond() - 1, NOW.getEpochSecond()));
        MockHttpServletResponse expired = new MockHttpServletResponse();
        filter.doFilter(request, expired, (req, res) -> { });
        assertEquals(401, expired.getStatus());
    }

    @Test
    void selfRegistrationCannotRequestAdminRole() {
        assertEquals("customer", RoleMapper.selfRegistrationRole("admin"));
        assertEquals("seller", RoleMapper.selfRegistrationRole("restaurant"));
        assertEquals("driver", RoleMapper.selfRegistrationRole("shipper"));
        assertEquals("restaurant", RoleMapper.toAndroidRole("seller"));
        assertEquals("shipper", RoleMapper.toAndroidRole("driver"));
    }

    private MockHttpServletResponse invoke(String method, String path, String uid) throws Exception {
        MockHttpServletRequest request = authorizedRequest(method, path,
                token(uid, NOW.getEpochSecond() + 3600, NOW.getEpochSecond()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        return response;
    }

    private MockHttpServletRequest authorizedRequest(String method, String path, String idToken) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.addHeader("Authorization", "Bearer " + idToken);
        return request;
    }

    private String token(String uid, long exp, long iat) throws Exception {
        Map<String, Object> header = Map.of("alg", "RS256", "kid", "test-kid", "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "aud", PROJECT_ID,
                "iss", "https://securetoken.google.com/" + PROJECT_ID,
                "sub", uid,
                "exp", exp,
                "iat", iat);
        String unsigned = encode(jsonMapper.writeValueAsBytes(header)) + "."
                + encode(jsonMapper.writeValueAsBytes(claims));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + encode(signer.sign());
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
