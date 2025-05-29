package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.MatchEvent;
import com.appbasics.onlinefootballmanager.model.MatchEventType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class CommentaryService {

    private final Map<MatchEventType, List<String>> templates = Map.of(
            MatchEventType.GOAL, List.of(
                    "{player} finds the net with a brilliant strike!",
                    "Goal! {player} scores for {team}!",
                    "{player} blasts it home! What a finish!"
            ),
            MatchEventType.ASSIST, List.of(
                    "{player} delivers a perfect pass for the assist.",
                    "Brilliant setup by {player}.",
                    "{player} with the visionary assist!"
            ),
            MatchEventType.YELLOW_CARD, List.of(
                    "Yellow card shown to {player} for a rash tackle.",
                    "{player} is booked for that challenge.",
                    "Caution for {player} — yellow card issued."
            ),
            MatchEventType.RED_CARD, List.of(
                    "Red card! {player} is sent off!",
                    "Disaster for {player} — a straight red!",
                    "{player} receives their marching orders!"
            ),
            MatchEventType.INJURY, List.of(
                    "{player} is down — looks like an injury.",
                    "Concern as {player} is helped off the pitch.",
                    "Injury forces {player} out of the game."
            ),
            MatchEventType.SHOT, List.of(
                    "{player} takes a shot from distance!",
                    "A powerful strike from {player}!",
                    "{player} tests the keeper with a shot."
            ),
            MatchEventType.FOUL, List.of(
                    "{player} commits a foul.",
                    "The referee spots a foul by {player}.",
                    "{player} brings down the opponent — foul."
            ),
            MatchEventType.SUBSTITUTION, List.of(
                    "{player} makes way for a substitution.",
                    "{player} is substituted by the coach.",
                    "Change for the team: {player} off."
            )
    );

    public String generateCommentary(MatchEvent event) {
        List<String> options = templates.get(event.getType());

        if (options == null || options.isEmpty()) {
            System.err.println("⚠️ Unknown MatchEventType: " + event.getType() +
                    " for player " + event.getPlayerName());
            return event.getPlayerName() + " was involved in the play.";
        }

        String template = options.get(new Random().nextInt(options.size()));
        return template
                .replace("{player}", event.getPlayerName())
                .replace("{team}", event.getTeamId());
    }
}
