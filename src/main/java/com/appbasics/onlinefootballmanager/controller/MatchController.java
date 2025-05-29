package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.Match;
import com.appbasics.onlinefootballmanager.model.MatchData;
import com.appbasics.onlinefootballmanager.model.MatchReplay;
import com.appbasics.onlinefootballmanager.model.MatchResult;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.MatchReplayRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.MatchRepository;
import com.appbasics.onlinefootballmanager.service.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    @Autowired
    private MatchService matchService;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private MatchReplayRepository matchReplayRepository;
    @Autowired
    private LeagueInstanceRepository leagueInstanceRepository;

    @GetMapping("/{instanceId}/{matchId}/{slotId}/simulate")
    public Mono<ResponseEntity<MatchData>> initializeMatch(
            @PathVariable String instanceId,
            @PathVariable String matchId,
            @PathVariable String slotId) {
        return matchService.initializeMatch(instanceId, matchId, slotId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{instanceId}/{matchId}/{slotId}/simulate")
    public Mono<ResponseEntity<MatchResult>> simulateMatch(
            @PathVariable String instanceId,
            @PathVariable String matchId,
            @PathVariable String slotId) {
        return matchService.initializeMatch(instanceId, matchId, slotId)
                .flatMap(matchService::simulateMatch)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{instanceId}/{matchId}/{slotId}/finalize")
    public Mono<ResponseEntity<String>> finalizeMatch(@PathVariable String instanceId,
                                                      @PathVariable String matchId,
                                                      @PathVariable String slotId) {

        return matchService.finalizeMatch(instanceId, matchId, slotId)
                .thenReturn(ResponseEntity.ok("Match finalized successfully"))
                .doOnSuccess(response -> System.out.println("Match finalized for: " + instanceId + " - " + matchId+ " with Slot: " + slotId))
                .onErrorResume(e -> {
                    System.err.println("❌ Error finalizing match: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error finalizing match"));
                });
    }


    @PostMapping("/{instanceId}/matchday/{matchday}/finalize")
    public Mono<ResponseEntity<String>> finalizeMatchday(@PathVariable String instanceId,
                                                         @PathVariable int matchday) {
        return matchService.finalizeMatchday(instanceId, matchday)
                .thenReturn(ResponseEntity.ok("Matchday " + matchday + " finalized successfully"))
                .doOnSuccess(response -> System.out.println("✅ Matchday finalized: " + matchday + " for league: " + instanceId))
                .onErrorResume(e -> {
                    System.err.println("❌ Error finalizing matchday: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error finalizing matchday"));
                });
    }

    @GetMapping("/{instanceId}/team/{teamId}/matchday/{matchday}")
    public Flux<Match> getTeamMatchesForMatchday(
            @PathVariable String instanceId,
            @PathVariable String teamId,
            @PathVariable int matchday) {
        return matchService.getMatchesForTeamOnMatchday(instanceId, teamId, matchday);
    }

    @GetMapping("/{instanceId}/matchday/{matchday}")
    public Flux<Match> getMatchesForMatchday(
            @PathVariable String instanceId,
            @PathVariable int matchday
    ) {
        return matchRepository.findAllByInstanceIdAndMatchday(instanceId, matchday);
    }

    @GetMapping("/team/{instanceId}/{teamId}")
    public Flux<Match> getMatchesForTeam(
            @PathVariable String instanceId,
            @PathVariable String teamId) {
        return matchRepository.findAllByInstanceId(instanceId)
                .filter(match ->
                        (match.getTeamA() != null && teamId.equals(match.getTeamA().getTeamId())) ||
                                (match.getTeamB() != null && teamId.equals(match.getTeamB().getTeamId())))
                .sort(Comparator.comparingInt(Match::getMatchday)
                        .thenComparing(Match::getMatchDate));
    }

    @GetMapping("/{instanceId}/matchdays")
    public Flux<Integer> getMatchdays(@PathVariable String instanceId) {
        return matchRepository.findAllByInstanceId(instanceId)
                .map(Match::getMatchday)
                .distinct()
                .sort();
    }

    @GetMapping("/{instanceId}/next-matchday")
    public Mono<Integer> getNextMatchday(@PathVariable String instanceId) {
        Instant now = Instant.now();

        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                .flatMap(instance -> {
                    if ("completed".equalsIgnoreCase(instance.getStatus())) {
                        return Mono.just(instance.getCurrentMatchday()); // return final matchday
                    }

                    return matchRepository.findAllByInstanceId(instanceId)
                            .filter(match -> match.getMatchDate() != null && match.getMatchDate().toInstant().isAfter(now))
                            .sort(Comparator.comparing(Match::getMatchDate))
                            .next()
                            .map(Match::getMatchday)
                            .defaultIfEmpty(instance.getCurrentMatchday()); // fallback safely
                });
    }


    @GetMapping("/match-replay/{matchId}")
    public Mono<MatchReplay> getReplay(@PathVariable String matchId) {
        return matchReplayRepository.findByMatchId(matchId);
    }

    @GetMapping("/{instanceId}/{matchId}")
    public Mono<ResponseEntity<Match>> getMatchById(
            @PathVariable String instanceId,
            @PathVariable String matchId) {
        return matchRepository.findByInstanceIdAndMatchId(instanceId, matchId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
