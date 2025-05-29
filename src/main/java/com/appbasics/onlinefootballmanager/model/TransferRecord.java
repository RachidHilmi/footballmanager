package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "transfer_records")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferRecord {
    @Id
    private String id;
    private String playerId;
    private String oldManagerId;
    private String newManagerId;
    private String oldTeamId;
    private String newTeamId;
    private String newLeagueId;
    private String newInstanceId;
    private String oldLeagueId;
    private String regionId;
    private double price;
    private String date;
    private boolean systemGenerated;
}

