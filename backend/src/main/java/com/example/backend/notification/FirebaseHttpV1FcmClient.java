package com.example.backend.notification;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class FirebaseHttpV1FcmClient implements FcmClient {
    private static final Logger log = LoggerFactory.getLogger(FirebaseHttpV1FcmClient.class);
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private final ObjectMapper json;
    private final HttpClient http;
    private final String serviceAccountFile;
    private final String projectId;
    private volatile CachedAccessToken cachedAccessToken;

    public FirebaseHttpV1FcmClient(ObjectMapper json,
            @Value("${FIREBASE_SERVICE_ACCOUNT_FILE:}") String serviceAccountFile,
            @Value("${foodhub.firebase.project-id:}") String projectId) {
        this.json = json;
        this.serviceAccountFile = serviceAccountFile;
        this.projectId = projectId;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public FcmSendResult send(String registrationToken, String title, String body, String type, String orderId) {
        if (blank(serviceAccountFile) || blank(projectId) || blank(registrationToken)) return FcmSendResult.FAILED;
        try {
            String accessToken = accessToken();
            Map<String,Object> message = new LinkedHashMap<>();
            message.put("token", registrationToken);
            message.put("notification", Map.of("title", value(title), "body", value(body)));
            message.put("data", Map.of("type", value(type), "orderId", value(orderId)));
            byte[] requestBody = json.writeValueAsBytes(Map.of("message", message));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return FcmSendResult.SENT;
            if (isDeadRegistration(response.statusCode(), response.body())) return FcmSendResult.UNREGISTERED;
            log.warn("Firebase Messaging request failed with HTTP {}", response.statusCode());
            return FcmSendResult.FAILED;
        } catch (Exception exception) {
            log.warn("Firebase Messaging delivery failed ({})", exception.getClass().getSimpleName());
            return FcmSendResult.FAILED;
        }
    }

    private String accessToken() throws Exception {
        CachedAccessToken current = cachedAccessToken;
        if (current != null && Instant.now().isBefore(current.refreshAfter())) return current.value();
        synchronized (this) {
            current = cachedAccessToken;
            if (current != null && Instant.now().isBefore(current.refreshAfter())) return current.value();
            JsonNode account = json.readTree(Files.readString(Path.of(serviceAccountFile)));
            String email = account.path("client_email").asString("");
            String privateKey = account.path("private_key").asString("");
            if (blank(email) || blank(privateKey)) throw new IllegalStateException("Service account is incomplete");
            long now = Instant.now().getEpochSecond();
            String header = base64Url(json.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
            String claim = base64Url(json.writeValueAsBytes(Map.of("iss", email, "scope", SCOPE,
                    "aud", TOKEN_URI, "iat", now, "exp", now + 3600)));
            String unsigned = header + "." + claim;
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(decodePrivateKey(privateKey))));
            signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            String assertion = unsigned + "." + base64Url(signer.sign());
            String form = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:jwt-bearer")
                    + "&assertion=" + encode(assertion);
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URI)).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("OAuth token endpoint returned HTTP " + response.statusCode());
            JsonNode token = json.readTree(response.body());
            String value = token.path("access_token").asString("");
            if (blank(value)) throw new IOException("OAuth response has no access token");
            long expiresIn = Math.max(60, token.path("expires_in").asLong(3600));
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            Instant refreshAfter = expiresAt.minusSeconds(Math.min(60, expiresIn / 10));
            cachedAccessToken = new CachedAccessToken(value, refreshAfter);
            return value;
        }
    }

    private boolean isDeadRegistration(int status, String responseBody) {
        if (status == 404) return true;
        try {
            JsonNode root = json.readTree(responseBody);
            if ("NOT_FOUND".equals(root.path("error").path("status").asString())) return true;
            JsonNode details = root.path("error").path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    if ("UNREGISTERED".equals(detail.path("errorCode").asString())) return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static byte[] decodePrivateKey(String pem) {
        String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(encoded);
    }
    private static String base64Url(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String value(String value) { return value == null ? "" : value; }
    private record CachedAccessToken(String value, Instant refreshAfter) { }
}
