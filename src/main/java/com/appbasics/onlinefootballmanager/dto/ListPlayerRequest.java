package com.appbasics.onlinefootballmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListPlayerRequest {
    private String regionId;
    private String leagueId;
    private String teamId;
    private String managerId;
    private String slotId;
    private String playerId;
    private int playerAge;
    private double askingPrice;
    private double playerMarketValue;
}

