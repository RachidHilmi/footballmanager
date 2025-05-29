package com.appbasics.onlinefootballmanager.model;

import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardInstance {
    private String instanceId;
    private String cardId;
    private String name;
    private String icon;
    private String rarity;
    private int usesLeft;
    private Date acquiredAt;
    private String source;
    private String category;
    private String type;
}
