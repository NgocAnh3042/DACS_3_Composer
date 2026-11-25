package com.example.backend.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogHttpResponse {
    private static final String CACHE_CONTROL = "private, max-age=60, stale-while-revalidate=120";
    private final ObjectMapper objectMapper;

    public CatalogHttpResponse(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public ResponseEntity<?> cached(Object body, String ifNoneMatch) {
        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(body);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi xử lý yêu cầu");
        }
        String etag = "\"" + HexFormat.of().formatHex(sha256(json)) + "\"";
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
        headers.setETag(etag);
        headers.setVary(List.of(HttpHeaders.ACCEPT_ENCODING));
        if (matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static boolean matches(String header, String etag) {
        if (header == null || header.isBlank()) return false;
        for (String candidate : header.split(",")) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed) || etag.equals(trimmed)) return true;
        }
        return false;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
