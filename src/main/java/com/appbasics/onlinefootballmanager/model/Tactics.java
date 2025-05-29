package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Tactics {
    private String attackers;
    private String defenders;
    private String gamePlan;
    private String marking;
    private String mentality;
    private String midfielders;
    private String offsideTrap;
    private String pressure;
    private String tackling;
    private String tempo;

    public int calculateTacticalEffect() {
        int effect = 0;

        if ("Passing Game".equals(gamePlan)) effect += 5;
        if ("Aggressive".equals(tackling)) effect += 3;
        if ("Close Down".equals(pressure)) effect += 4;
        if ("Zonal Marking".equals(marking)) effect += 2;
        if ("Yes".equals(offsideTrap)) effect += 3; // New factor

        return effect;
    }
}

