package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "standing")
@CompoundIndexes({
        @CompoundIndex(name = "instance_team_idx", def = "{'instanceId': 1, 'teamId': 1}", unique = true)
})
public class Standing {
    @Id
    private String _id;

    private String teamId;
    private String leagueId;
    private String instanceId;
    private String teamName;
    private String managerId;
    private String managerName;
    private int draws;
    private int goalsAgainst;
    private int goalsFor;
    private int losses;
    private int matchesPlayed;
    private int points;
    private int goalDifference;
    private int wins;
}

