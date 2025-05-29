package com.appbasics.onlinefootballmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchEvent {
    private String matchId;
    private MatchEventType type;         // goal, yellow_card, red_card, injury, shot, etc.
    private String teamId;
    private String playerId;
    private String playerName;
    private int minute;
    private String description; // "Player X scored with a header"


}
