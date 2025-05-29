package com.appbasics.onlinefootballmanager.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "players")
public class Player {

    @Id
    private String _id;

    private String playerId;
    private String teamId;
    private String originalTeamId;
    private String leagueId;
    private String instanceId;
    private String countryId;
    private String regionId;
    private String name;
    private int age;
    private String position;
    private String nationality;
    private String status;

    private Stats baseStats;
    private Stats currentStats;

    @Data
    public static class Stats {
        private String marketValue = "$0.5M";
        private int attackValue = 0;
        private int defenseValue = 0;
        private int overallValue = 0;
        private int moralLevel = 100;
        private int fitnessLevel = 100;

        private double expectedBoost = 0;
        private double pendingBoost = 0;
        private long lastTrainedAt = 0;
    }
}
