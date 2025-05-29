package com.appbasics.onlinefootballmanager.model;

import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "training_cards")
public class TrainingCard {
    private String cardId;
    private String name;
    private String description;
    private String type;       // ENUM string like "DOUBLE_BOOST", "INSTANT_FINISH"
    private String rarity;     // "COMMON", "RARE", etc.
    private int maxUses;
    private String icon;
    private String category;
}
