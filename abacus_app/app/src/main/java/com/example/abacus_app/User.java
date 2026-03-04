package com.example.abacus_app;

public class User {

    private String uid;
    private String email;
    private String name;
    private String createdAt;
    private boolean isDeleted;
    private String deletedAt;

    // Empty constructor required by Firestore
    public User() {}

    public User(String uid, String email, String name, String createdAt) {
        this.uid       = uid;
        this.email     = email;
        this.name      = name;
        this.createdAt = createdAt;
        this.isDeleted = false;
        this.deletedAt = "";
    }

    // Getters
    public String getUid()
    { return uid; }
    public String getEmail()
    { return email; }
    public String getName()
    { return name; }
    public String getCreatedAt()
    { return createdAt; }
    public boolean isDeleted()
    { return isDeleted; }
    public String getDeletedAt()
    { return deletedAt; }

    // Setters
    public void setUid(String uid)
    { this.uid = uid; }
    public void setEmail(String email)
    { this.email = email; }
    public void setName(String name)
    { this.name = name; }
    public void setCreatedAt(String createdAt)
    { this.createdAt = createdAt; }
    public void setDeleted(boolean deleted)
    { this.isDeleted = deleted; }
    public void setDeletedAt(String deletedAt)
    { this.deletedAt = deletedAt; }
}
