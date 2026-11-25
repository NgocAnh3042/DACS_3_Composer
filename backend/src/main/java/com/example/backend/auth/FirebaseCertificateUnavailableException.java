package com.example.backend.auth;

public class FirebaseCertificateUnavailableException extends RuntimeException {
    public FirebaseCertificateUnavailableException() {
        super("Firebase certificates are temporarily unavailable");
    }
}
