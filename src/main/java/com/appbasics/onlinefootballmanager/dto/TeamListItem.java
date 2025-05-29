package com.appbasics.onlinefootballmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamListItem {
    private String teamId;
    private String teamName;
    private String managerName;
    private String managerId;
    private boolean isOpponentToday;
    private boolean playedToday;
    private String lastResult;
}
