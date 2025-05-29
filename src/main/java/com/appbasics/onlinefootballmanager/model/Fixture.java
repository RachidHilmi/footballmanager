package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fixture {
    private int matchday;
    private String homeTeamId;
    private String awayTeamId;
    private int homeScore;
    private int awayScore;
    private boolean played;
}
