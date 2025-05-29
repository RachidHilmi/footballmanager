package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchResult {
    private String matchId;

    private int teamA_score;
    private int teamB_score;

    private List<MatchEvent> events;
    List<String> commentary;
    Map<String, Integer> playerRatings;
}
