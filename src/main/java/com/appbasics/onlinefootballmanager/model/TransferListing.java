package com.appbasics.onlinefootballmanager.model;

import com.google.cloud.spring.data.firestore.Document;
import com.google.cloud.firestore.annotation.PropertyName;
import lombok.*;
import org.springframework.data.annotation.Id;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Document(collectionName = "transfer_market")
public class TransferListing {
    @Id
    @PropertyName("id")
    private String id;
    @PropertyName("playerId")
    private String playerId;
    @PropertyName("playerAge")
    private int playerAge;
    @PropertyName("teamId")
    private String teamId; // seller
    @PropertyName("managerId")
    private String managerId; // seller's manager
    @PropertyName("regionId")
    private String regionId;
    @PropertyName("instanceId")
    private String instanceId;
    @PropertyName("slotId")
    private String slotId;
    @PropertyName("askingPrice")
    private double askingPrice;
    @PropertyName("playerMarketValue")
    private double playerMarketValue;
    @PropertyName("listedAt")
    private long listedAt; // timestamp
    @PropertyName("sold")
    private boolean sold;
    @PropertyName("buyerId")
    private String buyerId; // if sold
    @PropertyName("buyerTeamId")
    private String buyerTeamId;
    @PropertyName("buyerLeagueId")
    private String buyerLeagueId;
    @PropertyName("systemGenerated")
    private boolean systemGenerated;
    @PropertyName("playerName")
    private String playerName;
    @PropertyName("playerPosition")
    private String playerPosition;
    @PropertyName("playerNationality")
    private String playerNationality;
}

