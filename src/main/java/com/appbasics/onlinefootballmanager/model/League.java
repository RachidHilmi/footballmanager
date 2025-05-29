package com.appbasics.onlinefootballmanager.model;

import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collectionName = "leagues")
public class League {
    @Id
    private String id;
    private String leagueId;
    private String name;
    private String countryId;
    private String regionId;
    private int matchdays;
    private int currentMatchday;
    private String status;
    private long preparationStart;
    private int reservedTeams;
    private List<String> teams;
    private List<Match> fixtures;
}
