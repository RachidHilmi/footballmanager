package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchData {

    private Match match;
    private LeagueInstance.LeagueInstanceTeam teamA;
    private LeagueInstance.LeagueInstanceTeam teamB;
    private Formation formationA;
    private Formation formationB;
    private Tactics tacticsA;
    private Tactics tacticsB;
}
