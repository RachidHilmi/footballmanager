package com.appbasics.onlinefootballmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamSelectionRequest {
    private String managerId;
    private String managerName;
    private String instanceId;
    private String teamId;
}

