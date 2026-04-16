package com.evanlink.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume_recode")
public class ResumeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "name_apply")
    private String nameApply;
    
    private String password;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "time_apply")
    private LocalDateTime timeApply;
    
    @PrePersist
    protected void onCreate() {
        if (timeApply == null) {
            timeApply = LocalDateTime.now();
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getNameApply() { return nameApply; }
    public void setNameApply(String nameApply) { this.nameApply = nameApply; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public LocalDateTime getTimeApply() { return timeApply; }
    public void setTimeApply(LocalDateTime timeApply) { this.timeApply = timeApply; }
}
