package com.appbasics.onlinefootballmanager.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MatchEventType {
    GOAL,
    YELLOW_CARD,
    RED_CARD,
    INJURY,
    SHOT,
    FOUL,
    ASSIST,
    SUBSTITUTION;

    @JsonCreator
    public static MatchEventType fromValue(String value) {
        if (value == null) return null;
        try {
            return MatchEventType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            System.err.println("⚠️ Unknown MatchEventType: " + value);
            return null; // or a default like MatchEventType.UNKNOWN if defined
        }
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
