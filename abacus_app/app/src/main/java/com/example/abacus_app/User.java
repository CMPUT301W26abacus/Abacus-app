package com.example.abacus_app;

import com.google.firebase.firestore.PropertyName;

public class User {

    private String  uid;
    private String  email;
    private String  name;
    private String  phone;
    private String  createdAt;
    private boolean isDeleted;

    // deletedAt stored as long (epoch ms) — avoids Timestamp vs String mismatch
    private long    deletedAt;
    private long    lastLoginAt;

    // isGuest: use @PropertyName so Firestore uses "isGuest", not "guest"
    @PropertyName("isGuest")
    private boolean isGuest;
    
    // User role for Sprint 1 features (entrant, organizer, admin)
    private String role;
    
    // Notification preferences for US 01.04.03
    private boolean notificationsEnabled;

    // Required empty constructor for Firestore deserialization
    public User() {}

    public User(String uid, String email, String name, String createdAt) {
        this.uid         = uid;
        this.email       = email;
        this.name        = name;
        this.phone       = "";
        this.createdAt   = createdAt;
        this.isDeleted   = false;
        this.deletedAt   = 0L;
        this.lastLoginAt = 0L;
        this.isGuest     = true;
        this.role        = "entrant"; // Default role for Sprint 1
        this.notificationsEnabled = true; // Default: notifications enabled
    }

    public String  getUid()         { return uid; }
    public String  getEmail()       { return email; }
    public String  getName()        { return name; }
    public String  getPhone()       { return phone; }
    public String  getCreatedAt()   { return createdAt; }
    public boolean isDeleted()      { return isDeleted; }
    public long    getDeletedAt()   { return deletedAt; }
    public long    getLastLoginAt() { return lastLoginAt; }

    @PropertyName("isGuest")
    public boolean isGuest()        { return isGuest; }
    
    public String  getRole()        { return role; }
    
    public boolean getNotificationsEnabled() { return notificationsEnabled; }


    public void setUid(String uid)               { this.uid = uid; }
    public void setEmail(String email)           { this.email = email; }
    public void setName(String name)             { this.name = name; }
    public void setPhone(String phone)           { this.phone = phone; }
    public void setCreatedAt(String createdAt)   { this.createdAt = createdAt; }
    public void setDeleted(boolean deleted)      { this.isDeleted = deleted; }
    public void setDeletedAt(long deletedAt)     { this.deletedAt = deletedAt; }
    public void setLastLoginAt(long lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    /**
     * Setter for isGuest field.
     * @param isGuest Use @PropertyName so Firestore uses "isGuest", not "guest".
     */
    @PropertyName("isGuest")
    public void setIsGuest(boolean isGuest)      { this.isGuest = isGuest; }
    
    public void setRole(String role)             { this.role = role; }
    
    public void setNotificationsEnabled(boolean enabled) { this.notificationsEnabled = enabled; }
}