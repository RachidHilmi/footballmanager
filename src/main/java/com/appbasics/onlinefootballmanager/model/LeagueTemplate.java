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
@Document(collection = "leagueTemplates")
public class LeagueTemplate {

    @Id
    private String _id;

    private String templateId;
    private String regionId;
    private String countryId;
    private String name;
    private String flag;
    private int matchdays;
    private int totalTeams;

    private List<LeagueTemplateTeam> teams;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeagueTemplateTeam {
        private String teamId;
        private String teamName;
        private List<String> defaultPlayerIds;
        private String teamObjective;
        private String initialSquadValue;
    }
}
