package com.example.backend.auth;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GoogleFirebaseCertificateProvider implements FirebasePublicCertificateProvider {
    private static final String CERTIFICATE_URL =
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
    private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final long FALLBACK_TTL_SECONDS = 300;

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private volatile CachedCertificates cached;

    public GoogleFirebaseCertificateProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, PublicKey> certificates(boolean forceRefresh) {
        CachedCertificates current = cached;
        if (!forceRefresh && current != null && Instant.now().isBefore(current.expiresAt())) {
            return current.keys();
        }
        synchronized (this) {
            current = cached;
            if (!forceRefresh && current != null && Instant.now().isBefore(current.expiresAt())) {
                return current.keys();
            }
            return refresh();
        }
    }

    private Map<String, PublicKey> refresh() {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(CERTIFICATE_URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new FirebaseCertificateUnavailableException();
            }
            Map<String, String> pemByKid = objectMapper.readValue(
                    response.body(), new TypeReference<>() { });
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Map<String, PublicKey> keys = new HashMap<>();
            for (Map.Entry<String, String> entry : pemByKid.entrySet()) {
                var certificate = factory.generateCertificate(new ByteArrayInputStream(
                        entry.getValue().getBytes(StandardCharsets.US_ASCII)));
                keys.put(entry.getKey(), certificate.getPublicKey());
            }
            if (keys.isEmpty()) {
                throw new FirebaseCertificateUnavailableException();
            }
            Map<String, PublicKey> immutableKeys = Map.copyOf(keys);
            cached = new CachedCertificates(immutableKeys, Instant.now().plusSeconds(ttlSeconds(response)));
            return immutableKeys;
        } catch (FirebaseCertificateUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FirebaseCertificateUnavailableException();
        }
    }

    private long ttlSeconds(java.net.http.HttpResponse<?> response) {
        String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
        Matcher matcher = MAX_AGE.matcher(cacheControl);
        long ttl = FALLBACK_TTL_SECONDS;
        if (matcher.find()) {
            ttl = Long.parseLong(matcher.group(1));
            long age = response.headers().firstValue("Age").map(GoogleFirebaseCertificateProvider::parseLong)
                    .orElse(0L);
            ttl = Math.max(0, ttl - age);
        } else {
            String expires = response.headers().firstValue("Expires").orElse(null);
            if (expires != null) {
                try {
                    ttl = Math.max(0, Duration.between(Instant.now(),
                            ZonedDateTime.parse(expires, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds());
                } catch (RuntimeException ignored) {
                    ttl = FALLBACK_TTL_SECONDS;
                }
            }
        }
        return Math.min(ttl, Duration.ofDays(1).toSeconds());
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record CachedCertificates(Map<String, PublicKey> keys, Instant expiresAt) { }
}
