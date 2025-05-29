package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.*;
import com.appbasics.onlinefootballmanager.websocket.MatchWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MatchEventService {

    private final MatchWebSocketHandler webSocketHandler;
    private final CommentaryService commentaryService;

    @Autowired
    public MatchEventService(MatchWebSocketHandler webSocketHandler, CommentaryService commentaryService) {
        this.webSocketHandler = webSocketHandler;
        this.commentaryService = commentaryService;
    }


    public void broadcastMatchEvent(String matchId, MatchEvent event) {
        webSocketHandler.broadcastToMatch(matchId, event);
    }

    public void broadcastMatchUpdate(String matchId, MatchResult result) {
        webSocketHandler.broadcastToMatch(matchId, result);
    }

    public void broadcastInProgressMatch(Match match) {
        webSocketHandler.broadcastToMatch(match.getMatchId(), match);
    }

    public List<MatchEvent> simulateEventsForTeam(String matchId, String teamId, List<Player> players, int goalsScored) {
        List<MatchEvent> events = new ArrayList<>();
        Random random = new Random();

        if (players == null || players.isEmpty()) {
            System.err.println("⚠️ No players found for team: " + teamId + ", cannot simulate events.");
            return events;
        }

        for (int i = 0; i < goalsScored; i++) {
            Player scorer = players.get(random.nextInt(players.size()));
            int minute = 5 + random.nextInt(80);

            MatchEvent goal = new MatchEvent(matchId, MatchEventType.GOAL, teamId, scorer.get_id(), scorer.getName(), minute, "");
            goal.setDescription(commentaryService.generateCommentary(goal));
            events.add(goal);
            broadcastMatchEvent(matchId, goal);

            if (random.nextDouble() < 0.8) {
                Player assist = players.get(random.nextInt(players.size()));
                if (!assist.get_id().equals(scorer.get_id())) {
                    MatchEvent assistEvent = new MatchEvent(matchId, MatchEventType.ASSIST, teamId, assist.get_id(), assist.getName(), minute, "");
                    assistEvent.setDescription(commentaryService.generateCommentary(assistEvent));
                    events.add(assistEvent);
                    broadcastMatchEvent(matchId, assistEvent);
                }
            }
        }

        for (Player player : players) {
            int minute = random.nextInt(90);

            if (random.nextDouble() < 0.10) {
                MatchEvent e = new MatchEvent(matchId, MatchEventType.SHOT, teamId, player.get_id(), player.getName(), minute, "");
                e.setDescription(commentaryService.generateCommentary(e));
                events.add(e); broadcastMatchEvent(matchId, e);
            }

            if (random.nextDouble() < 0.03) {
                MatchEvent e = new MatchEvent(matchId, MatchEventType.YELLOW_CARD, teamId, player.get_id(), player.getName(), minute, "");
                e.setDescription(commentaryService.generateCommentary(e));
                events.add(e); broadcastMatchEvent(matchId, e);
            }

            if (random.nextDouble() < 0.01) {
                MatchEvent e = new MatchEvent(matchId, MatchEventType.RED_CARD, teamId, player.get_id(), player.getName(), minute, "");
                e.setDescription(commentaryService.generateCommentary(e));
                events.add(e); broadcastMatchEvent(matchId, e);
            }

            if (random.nextDouble() < 0.02) {
                MatchEvent e = new MatchEvent(matchId, MatchEventType.INJURY, teamId, player.get_id(), player.getName(), minute, "");
                e.setDescription(commentaryService.generateCommentary(e));
                events.add(e); broadcastMatchEvent(matchId, e);
            }

            if (random.nextDouble() < 0.05) {
                MatchEvent e = new MatchEvent(matchId, MatchEventType.FOUL, teamId, player.get_id(), player.getName(), minute, "");
                e.setDescription(commentaryService.generateCommentary(e));
                events.add(e); broadcastMatchEvent(matchId, e);
            }
        }

        for (int i = 0; i < 3 && players.size() > 1; i++) {
            Player sub = players.get(random.nextInt(players.size()));
            int minute = 45 + random.nextInt(45);
            MatchEvent e = new MatchEvent(matchId, MatchEventType.SUBSTITUTION, teamId, sub.get_id(), sub.getName(), minute, "");
            e.setDescription(commentaryService.generateCommentary(e));
            events.add(e);
            broadcastMatchEvent(matchId, e);
        }

        return events;
    }

}
