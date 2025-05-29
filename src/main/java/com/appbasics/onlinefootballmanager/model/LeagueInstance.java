package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "leagueInstances")
public class LeagueInstance {

    @Id
    private String _id; // e.g., Africa_Morocco_01_instance_001

    private String instanceId;
    private String leagueId; // e.g., Morocco_01
    private String templateId; // e.g., Africa_Morocco_01
    private String regionId;
    private String countryId;
    private String season;
    private boolean available;
    private int currentMatchday;
    private int reservedTeams;
    private Object preparationStart; // use Date if needed
    private String status;

    private List<ReservedTeam> reservedTeamsList;
    private List<LeagueInstanceTeam> teams;
    private List<Standing> standings;
    //private List<Fixture> fixtures;
    private List<TransferRecord> transfers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservedTeam {
        private String teamId;
        private String managerId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeagueInstanceTeam {
        private String teamId;
        private List<String> currentPlayerIds;
        private String currentSquadValue;
        private String managerId;
        private boolean available;
        private String managerName;
        private String teamName;
        private String leagueId;
        private String instanceId;
    }
}
