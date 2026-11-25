package com.example.backend.auth;

public class FirebaseTokenException extends RuntimeException {
    public FirebaseTokenException() {
        super("Firebase ID token is invalid");
    }
}
