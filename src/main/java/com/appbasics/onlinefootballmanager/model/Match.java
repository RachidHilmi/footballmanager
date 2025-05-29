package com.appbasics.onlinefootballmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "matches")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Match {
    @Id
    private String _id;
    private String leagueId;
    private String instanceId;
    private String matchId;
    private Date matchDate;
    private int matchday;
    private String season;
    private String status;

    @Field("result")
    private MatchResult result;

    private TeamDetails teamA;
    private TeamDetails teamB;
    private String winner;

    private Formation formationA;
    private Formation formationB;

    private Tactics tacticsA;
    private Tactics tacticsB;

    private String slotIdA;
    private String slotIdB;
}
