package com.evanlink.auth;
import jakarta.persistence.*;
@Entity
@Table(name="revoked_admin_session")
public class RevokedAdminSession {
    @Id public String tokenHash;
    public long expiresAt;
}
