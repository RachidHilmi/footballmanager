package com.appbasics.onlinefootballmanager.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "match_replays")
public class MatchReplay {
    @Id
    private String matchId;
    private List<MatchEvent> events;
    private Map<String, Integer> playerRatings;
}
