package com.appbasics.onlinefootballmanager.model;

import com.google.firebase.database.PropertyName;
import lombok.*;

import java.util.Date;

@Data
public class Slot {
    private String id;
    private boolean available;
    private String managerId; // If assigned, otherwise can be empty or null
    private String teamId;
    private String regionId;
    private String countryId;
    private String leagueId;
    private String instanceId;
    private String budget;
    private Date startDate; // You can use java.util.Date or String formatted date
    private Formation lineup;
    private Tactics tactics;

    // Constructors
    public Slot() {}

    public Slot(String id, boolean available, String managerId, String teamId,
                String regionId, String countryId, String leagueId, String instanceId, String budget, Date startDate) {
        this.id = id;
        this.available = available;
        this.managerId = managerId;
        this.teamId = teamId;
        this.regionId = regionId;
        this.countryId = countryId;
        this.leagueId = leagueId;
        this.instanceId = instanceId;
        this.budget = budget;
        this.startDate = startDate;
    }

    public boolean getAvailable() {
        return available;
    }

    // Getters and setters
}
