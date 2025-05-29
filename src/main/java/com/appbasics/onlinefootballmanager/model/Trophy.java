package com.appbasics.onlinefootballmanager.model;

import lombok.Data;

import java.util.List;

@Data

public class Trophy {
    private String id;
    private String name;
    private List<String> type; // ["league", "cup", "objective"]
    private String dateWon;
    private List<String> objectivesCompleted;

    public Trophy() {}

    public Trophy(String id, String name, List<String> type, String dateWon, List<String> objectivesCompleted) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.dateWon = dateWon;
        this.objectivesCompleted = objectivesCompleted;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getType() {
        return type;
    }

    public void setType(List<String> type) {
        this.type = type;
    }

    public String getDateWon() {
        return dateWon;
    }

    public void setDateWon(String dateWon) {
        this.dateWon = dateWon;
    }

    public List<String> getObjectivesCompleted() {
        return objectivesCompleted;
    }

    public void setObjectivesCompleted(List<String> objectivesCompleted) {
        this.objectivesCompleted = objectivesCompleted;
    }
}