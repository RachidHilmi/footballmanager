package com.appbasics.onlinefootballmanager.dto;

import lombok.Data;

@Data
public class LeagueSelectionRequest {
    private String managerId;
    private String regionId;
    private String countryId;
    private String baseLeagueId;
}
