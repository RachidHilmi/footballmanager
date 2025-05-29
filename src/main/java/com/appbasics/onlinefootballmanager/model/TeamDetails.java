package com.appbasics.onlinefootballmanager.model;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamDetails {
    private String managerId;
    private String managerName;
    private String teamId;
    private String teamName;
}
