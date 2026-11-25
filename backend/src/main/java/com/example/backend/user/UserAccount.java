package com.example.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    private Long id;

    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected UserAccount() { }

    public UserAccount(Long id, String firebaseUid, String role, String status, LocalDateTime deletedAt) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.role = role;
        this.status = status;
        this.deletedAt = deletedAt;
    }

    public Long getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
