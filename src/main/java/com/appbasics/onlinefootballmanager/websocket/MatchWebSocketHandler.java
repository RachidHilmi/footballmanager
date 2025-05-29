package com.appbasics.onlinefootballmanager.websocket;

import com.appbasics.onlinefootballmanager.model.MatchEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MatchWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<String, List<Object>> cachedEvents = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessionMatchMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("🟢 Connected: " + session.getId());

        String uri = session.getUri() != null ? session.getUri().toString() : "";
        String matchId = extractMatchIdFromUri(uri);
        System.out.println("📤 matchId from Uri " + matchId + " matchId");
        if (matchId != null) {
            sessionMatchMap.put(session, matchId);

            // Replay cached events
            if (cachedEvents.containsKey(matchId)) {
                for (Object event : cachedEvents.get(matchId)) {
                    try {
                        session.sendMessage(new TextMessage(mapper.writeValueAsString(event)));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private String extractMatchIdFromUri(String uri) {
        try {
            URI parsed = new URI(uri);
            String query = parsed.getQuery(); // matchId=Americas_Brazil_01_instance_002_match_145
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("matchId=")) {
                        return param.split("=")[1];
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // No-op: server only sends messages
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        sessions.remove(session);
        System.out.println("🔴 Disconnected: " + session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public void broadcastToMatch(String matchId, Object data) {
        cachedEvents.computeIfAbsent(matchId, k -> new ArrayList<>()).add(data);
        try {
            String payload = mapper.writeValueAsString(data);
            TextMessage message = new TextMessage(payload);

            for (WebSocketSession session : sessions) {
                if (session.isOpen() && matchId.equals(sessionMatchMap.get(session))) {
                    session.sendMessage(message);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
