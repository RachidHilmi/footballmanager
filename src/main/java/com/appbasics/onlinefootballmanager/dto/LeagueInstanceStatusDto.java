package com.appbasics.onlinefootballmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeagueInstanceStatusDto {
    private String instanceId;
    private String leagueId;     // base leagueId (e.g. Morocco_01)
    private String templateId;   // from template
    private String regionId;
    private String countryId;
    private String season;
    private String status;
    private int currentMatchday;
    private int reservedTeams;
    private boolean available;

    private List<String> managerIds;    // from reservedTeamsList
    private int totalTeams;             // teams.size()
}
