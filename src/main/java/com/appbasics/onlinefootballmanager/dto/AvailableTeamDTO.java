package com.appbasics.onlinefootballmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableTeamDTO {
    private String teamId;
    private String teamName;
    private String teamObjective;
    private String currentSquadValue;
    private String leagueId;
    private String countryId;
    private int totalTeams;
    private int reservedTeams;
}
