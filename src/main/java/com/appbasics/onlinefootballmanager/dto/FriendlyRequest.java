package com.appbasics.onlinefootballmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendlyRequest {
    private String instanceId;
    private String initiatorTeamId;
    private String targetTeamId;
}

