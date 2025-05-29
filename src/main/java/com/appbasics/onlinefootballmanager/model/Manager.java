    package com.appbasics.onlinefootballmanager.model;

    import com.google.cloud.firestore.annotation.DocumentId;
    import com.google.cloud.spring.data.firestore.Document;
    import lombok.*;

    import java.util.Date;
    import java.util.List;
    import java.util.Map;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collectionName = "managers")

    public class Manager {
        @DocumentId
        private transient String documentId;

        private String managerId;
        private String id;
        private String name;
        private String email;
        private String password;
        private String icon;
        private String nationality;
        private int ranking;
        private int coins;
        private int dominationPoints;
        private int managerPoints;
        private int leaguesWon;
        private int cupsWon;
        private int objectivesCompleted;
        private String activeSlot;
        private List<String> chatReferences;
        private Map<String, Slot> slots;
        private int activeLevel;
        private Date lastLogin;
    }
