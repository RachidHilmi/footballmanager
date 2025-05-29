package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "friendlyMatches")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendlyMatch {
    @Id
    private String id;
    private String instanceId;
    private String initiatorManagerId;
    private String targetManagerId;
    private String initiatorTeamId;
    private String targetTeamId;
    private Date playedAt;
    private MatchResult result;
}

