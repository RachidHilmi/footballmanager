package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.MatchEvent;
import com.appbasics.onlinefootballmanager.model.MatchEventType;
import com.appbasics.onlinefootballmanager.model.MatchReplay;
import com.appbasics.onlinefootballmanager.repository.mongo.MatchReplayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/match-studio")
public class MatchStudioController {

    @Autowired
    private MatchReplayRepository matchReplayRepository;

    @GetMapping("/{matchId}/replay")
    public Mono<ResponseEntity<MatchReplay>> getMatchReplay(@PathVariable String matchId) {
        return matchReplayRepository.findByMatchId(matchId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{matchId}/summary")
    public Mono<ResponseEntity<List<MatchEvent>>> getKeyEvents(@PathVariable String matchId) {
        Set<MatchEventType> keyTypes = Set.of(
                MatchEventType.GOAL,
                MatchEventType.ASSIST,
                MatchEventType.RED_CARD,
                MatchEventType.YELLOW_CARD,
                MatchEventType.INJURY
        );

        return matchReplayRepository.findByMatchId(matchId)
                .map(replay -> {
                    List<MatchEvent> keyEvents = replay.getEvents().stream()
                            .filter(e -> keyTypes.contains(e.getType()))
                            .sorted(Comparator.comparingInt(MatchEvent::getMinute))
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(keyEvents);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
