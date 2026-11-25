package com.example.backend.auth;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FirebaseTokenVerifier {
    private final FirebasePublicCertificateProvider certificateProvider;
    private final ObjectMapper objectMapper;
    private final String projectId;
    private final Clock clock;

    @Autowired
    public FirebaseTokenVerifier(
            FirebasePublicCertificateProvider certificateProvider,
            ObjectMapper objectMapper,
            @Value("${foodhub.firebase.project-id}") String projectId) {
        this(certificateProvider, objectMapper, projectId, Clock.systemUTC());
    }

    FirebaseTokenVerifier(FirebasePublicCertificateProvider certificateProvider,
            ObjectMapper objectMapper, String projectId, Clock clock) {
        this.certificateProvider = certificateProvider;
        this.objectMapper = objectMapper;
        this.projectId = projectId;
        this.clock = clock;
    }

    public FirebaseClaims verify(String token) {
        try {
            String[] sections = token.split("\\.", -1);
            if (sections.length != 3) throw new FirebaseTokenException();

            Map<String, Object> header = decodeJson(sections[0]);
            if (!"RS256".equals(header.get("alg")) || !(header.get("kid") instanceof String kid)
                    || kid.isBlank()) {
                throw new FirebaseTokenException();
            }

            var key = certificateProvider.certificates(false).get(kid);
            if (key == null) key = certificateProvider.certificates(true).get(kid);
            if (key == null) throw new FirebaseTokenException();

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((sections[0] + "." + sections[1]).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(Base64.getUrlDecoder().decode(sections[2]))) {
                throw new FirebaseTokenException();
            }

            Map<String, Object> claims = decodeJson(sections[1]);
            if (!projectId.equals(claims.get("aud"))) throw new FirebaseTokenException();
            if (!(claims.get("iss") instanceof String issuer)
                    || !("https://securetoken.google.com/" + projectId).equals(issuer)) {
                throw new FirebaseTokenException();
            }
            if (!(claims.get("sub") instanceof String uid) || uid.isBlank()) {
                throw new FirebaseTokenException();
            }
            long now = clock.instant().getEpochSecond();
            long expiresAt = numericClaim(claims.get("exp"));
            long issuedAt = numericClaim(claims.get("iat"));
            if (expiresAt <= now || issuedAt > now + 60) throw new FirebaseTokenException();
            return new FirebaseClaims(uid, claims);
        } catch (FirebaseCertificateUnavailableException exception) {
            throw exception;
        } catch (FirebaseTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FirebaseTokenException();
        }
    }

    private Map<String, Object> decodeJson(String encoded) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        return objectMapper.readValue(bytes, new TypeReference<>() { });
    }

    private long numericClaim(Object value) {
        if (value instanceof Number number) return number.longValue();
        throw new FirebaseTokenException();
    }

    public record FirebaseClaims(String uid, Map<String, Object> values) { }
}
