package com.appbasics.onlinefootballmanager.model;

import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "transferOffer")
public class TransferOffer {
    @Id
    private String offerId;

    private String fromTeamId; // null or "AI" for AI teams
    private String toTeamId; // seller manager
    private String playerId;
    private String regionId;
    private String instanceId;
    private String fromInstanceId;
    private double offerPrice;
    private long offeredAt;
    private boolean accepted;
    private boolean rejected;
    private Double counterOfferPrice;
}