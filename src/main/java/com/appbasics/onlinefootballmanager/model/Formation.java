package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Formation {

    private String formation;
    private Map<String, String> positions;

    public int calculateFormationImpact() {
        switch (formation) {
            case "4_4_2": return 8;
            case "4_3_3": return 10;
            case "3_5_2": return 7;
            default: return 5;
        }
    }
}
