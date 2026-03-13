package com.example.abacus_app;

import com.google.firebase.Timestamp;
import java.io.Serializable;

/**
 * Entity class representing an Event in the system.
 * Contains information about the event details, registration period, 
 * capacity limits, and organizer settings.
 */
public class Event implements Serializable {
    private String eventId;
    private String title;
    private String description;
    private String organizerId;
    private Timestamp registrationStart;
    private Timestamp registrationEnd;
    private Integer waitlistCapacity; 
    private Integer eventCapacity;    
    private Integer waitlistCount;    // Current number of people on waitlist
    private boolean geoRequired;
    private String posterImageUrl;
    private String qrCodeUrl;

    public Event() {}

    public Event(String eventId, String title, String description, String organizerId, 
                 Timestamp registrationStart, Timestamp registrationEnd, 
                 Integer waitlistCapacity, Integer eventCapacity, boolean geoRequired) {
        this.eventId = eventId;
        this.title = title;
        this.description = description;
        this.organizerId = organizerId;
        this.registrationStart = registrationStart;
        this.registrationEnd = registrationEnd;
        this.waitlistCapacity = waitlistCapacity;
        this.eventCapacity = eventCapacity;
        this.geoRequired = geoRequired;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    public Timestamp getRegistrationStart() { return registrationStart; }
    public void setRegistrationStart(Timestamp registrationStart) { this.registrationStart = registrationStart; }

    public Timestamp getRegistrationEnd() { return registrationEnd; }
    public void setRegistrationEnd(Timestamp registrationEnd) { this.registrationEnd = registrationEnd; }

    public Integer getWaitlistCapacity() { return waitlistCapacity; }
    public void setWaitlistCapacity(Integer waitlistCapacity) { this.waitlistCapacity = waitlistCapacity; }

    public Integer getEventCapacity() { return eventCapacity; }
    public void setEventCapacity(Integer eventCapacity) { this.eventCapacity = eventCapacity; }

    public Integer getWaitlistCount() { return waitlistCount; }
    public void setWaitlistCount(Integer waitlistCount) { this.waitlistCount = waitlistCount; }

    public boolean isGeoRequired() { return geoRequired; }
    public void setGeoRequired(boolean geoRequired) { this.geoRequired = geoRequired; }

    public String getPosterImageUrl() { return posterImageUrl; }
    public void setPosterImageUrl(String posterImageUrl) { this.posterImageUrl = posterImageUrl; }

    public String getQrCodeUrl() { return qrCodeUrl; }
    public void setQrCodeUrl(String qrCodeUrl) { this.qrCodeUrl = qrCodeUrl; }
}