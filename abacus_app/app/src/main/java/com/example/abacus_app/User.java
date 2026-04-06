package com.example.abacus_app;

import com.google.firebase.firestore.PropertyName;
import java.util.Map;

/**
 * User.java
 *
 * This class represents a user profile within the Abacus application.
 * It is a Plain Old Java Object (POJO) used for Firestore serialization.
 * It stores personal information, preferences, role-based metadata,
 * and system settings like notification toggles.
 *
 * Role: Model/Entity in the Data Layer.
 */
public class User {

    private String    uid;
    private String    email;
    private String    name;
    private String    phone;
    private String createdAt;
    private boolean   isDeleted;
    private long      deletedAt;
    
    @PropertyName("lastLoginAt")
    private String    lastLoginAt;

    @PropertyName("isGuest")
    private boolean isGuest;

    private String  role;
    private boolean notificationsEnabled;
    private String  status; // "winner" or "loser"

    private String profilePhotoUrl;
    private String verificationStatus; // "none" | "email_verified" | "phone_verified"
    private String preferredLanguage;
    private String timezone;
    private String bio;
    private String organizationName;   // non-null only when role == "organizer"
    private Map<String, Object> preferences; // entrant event preferences

    /**
     * Default no-argument constructor required for Firebase Firestore deserialization.
     * Initializes notificationsEnabled to true by default for new accounts.
     */
    public User() {
        this.notificationsEnabled = true; // Push notifications default to ON
    }

    /**
     * Constructs a new User with basic identity information.
     *
     * @param uid       The unique identifier for the user.
     * @param email     The user's email address.
     * @param name      The user's display name.
     * @param createdAt The creation timestamp.
     */
    public User(String uid, String email, String name, String createdAt) {
        this.uid         = uid;
        this.email       = email;
        this.name        = name;
        this.phone       = "";
        this.createdAt   = null;
        this.isDeleted   = false;
        this.deletedAt   = 0L;
        this.lastLoginAt = null;
        this.isGuest     = true;
        this.role        = "entrant";
        this.notificationsEnabled = true; // Push notifications default to ON
        this.status      = "";
    }

    /** @return The user's unique ID. */
    public String    getUid()         { return uid; }
    /** @return The user's email address. */
    public String    getEmail()       { return email; }
    /** @return The user's display name. */
    public String    getName()        { return name; }
    /** @return The user's phone number. */
    public String    getPhone()       { return phone; }
    /** @return The date string when the account was created. */
    public String getCreatedAt()   { return createdAt; }
    /** @return True if the user account is soft-deleted. */
    public boolean   isDeleted()      { return isDeleted; }
    /** @return The timestamp when the account was deleted. */
    public long      getDeletedAt()   { return deletedAt; }
    
    /** @return The timestamp of the last login. */
    @PropertyName("lastLoginAt")
    public String    getLastLoginAt() { return lastLoginAt; }

    /** @return True if the user is a guest (not fully registered with email). */
    @PropertyName("isGuest")
    public boolean isGuest()          { return isGuest; }

    /** @return The user's role (e.g., "entrant", "organizer", "admin"). */
    public String  getRole()          { return role; }
    /** @return True if push notifications are enabled for this user. */
    public boolean getNotificationsEnabled() { return notificationsEnabled; }
    /** @return The user's lottery status for an event (e.g., "winner", "loser"). */
    public String  getStatus()        { return status; }


    public void setUid(String uid)               { this.uid = uid; }
    public void setEmail(String email)           { this.email = email; }
    public void setName(String name)             { this.name = name; }
    public void setPhone(String phone)           { this.phone = phone; }
    public void setCreatedAt(String createdAt){ this.createdAt = createdAt; }
    
    @PropertyName("lastLoginAt")
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    
    public void setDeleted(boolean deleted)      { this.isDeleted = deleted; }
    public void setDeletedAt(long deletedAt)     { this.deletedAt = deletedAt; }

    /**
     * Setter for isGuest field.
     * @param isGuest Use @PropertyName so Firestore uses "isGuest", not "guest".
     */
    @PropertyName("isGuest")
    public void setIsGuest(boolean isGuest)      { this.isGuest = isGuest; }

    public void setRole(String role)             { this.role = role; }
    /**
     * Sets whether push notifications are enabled.
     * @param enabled True to enable, false to disable.
     */
    public void setNotificationsEnabled(boolean enabled) { this.notificationsEnabled = enabled; }
    public void setStatus(String status)         { this.status = status; }

    /** @return The URL of the user's profile photo. */
    public String getProfilePhotoUrl()           { return profilePhotoUrl; }
    public void setProfilePhotoUrl(String url)   { this.profilePhotoUrl = url; }

    /** @return The verification status (e.g., "email_verified"). */
    public String getVerificationStatus()        { return verificationStatus; }
    public void setVerificationStatus(String s)  { this.verificationStatus = s; }

    /** @return The user's preferred language code. */
    public String getPreferredLanguage()         { return preferredLanguage; }
    public void setPreferredLanguage(String s)   { this.preferredLanguage = s; }

    /** @return The user's timezone ID. */
    public String getTimezone()                  { return timezone; }
    public void setTimezone(String s)            { this.timezone = s; }

    /** @return The user's short biography text. */
    public String getBio()                       { return bio; }
    public void setBio(String bio)               { this.bio = bio; }

    /** @return The name of the organization (only for organizers). */
    public String getOrganizationName()          { return organizationName; }
    public void setOrganizationName(String s)    { this.organizationName = s; }

    /** @return A map of the user's event-related preferences. */
    public Map<String, Object> getPreferences()  { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }
}
