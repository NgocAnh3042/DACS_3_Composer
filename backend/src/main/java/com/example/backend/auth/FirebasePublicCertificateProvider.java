package com.example.backend.auth;

import java.security.PublicKey;
import java.util.Map;

public interface FirebasePublicCertificateProvider {
    Map<String, PublicKey> certificates(boolean forceRefresh);
}
