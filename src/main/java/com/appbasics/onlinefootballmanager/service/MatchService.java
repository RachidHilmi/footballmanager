package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.*;
import com.appbasics.onlinefootballmanager.repository.firestore.ManagerRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

@Service
public class MatchService {

    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private LeagueInstanceRepository leagueInstanceRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private ManagerRepository managerRepository;
    @Autowired
    private MatchEventService matchEventService;
    @Autowired
    private Firestore firestore;
    @Autowired
    private LeagueService leagueService;
    @Autowired
    private MatchReplayRepository matchReplayRepository;
    @Autowired
    private TransferOfferRepository transferOfferRepository;
    @Autowired
    private CommentaryService commentaryService;

    private static final int[] GOALS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final double[] WEIGHTS = {0.20, 0.25, 0.22, 0.15, 0.08, 0.05, 0.03, 0.02, 0.01};

    public Mono<MatchData> initializeMatch(String instanceId, String matchId, String slotId) {
        return matchRepository.findByInstanceIdAndMatchId(instanceId, matchId)
                .flatMap(match -> leagueInstanceRepository.findFirstByInstanceId(instanceId)
                        .flatMap(instance -> {
                            LeagueInstance.LeagueInstanceTeam teamA = instance.getTeams().stream()
                                    .filter(t -> t.getTeamId().equals(match.getTeamA().getTeamId()))
                                    .findFirst().orElse(null);

                            LeagueInstance.LeagueInstanceTeam teamB = instance.getTeams().stream()
                                    .filter(t -> t.getTeamId().equals(match.getTeamB().getTeamId()))
                                    .findFirst().orElse(null);

                            if (teamA == null || teamB == null) {
                                return Mono.error(new IllegalStateException("Team not found in LeagueInstance"));
                            }

                            teamA.setInstanceId(instance.getInstanceId());
                            teamB.setInstanceId(instance.getInstanceId());

                            return Mono.zip(
                                    managerRepository.findByManagerId(teamA.getManagerId()).defaultIfEmpty(new Manager()),
                                    managerRepository.findByManagerId(teamB.getManagerId()).defaultIfEmpty(new Manager())
                            ).map(tuple -> {
                                Manager managerA = tuple.getT1();
                                Manager managerB = tuple.getT2();

                                Slot slotA = isHuman(managerA) ? managerA.getSlots().getOrDefault(slotId, new Slot()) : generateAISlot();
                                Slot slotB = isHuman(managerB) ? managerB.getSlots().getOrDefault(slotId, new Slot()) : generateAISlot();

                                Tactics tacticsA = Optional.ofNullable(slotA.getTactics()).orElse(generateAITactics());
                                Tactics tacticsB = Optional.ofNullable(slotB.getTactics()).orElse(generateAITactics());

                                return new MatchData(match, teamA, teamB, slotA.getLineup(), slotB.getLineup(), tacticsA, tacticsB);
                            });
                        }));
    }
    public Tactics generateAITactics() {
        Random random = new Random();
        return new Tactics(
                generateRandomTactic("attackers"),  // attackers
                generateRandomTactic("defenders"),  // defenders
                generateRandomTactic("gamePlan"),  // gamePlan
                generateRandomTactic("marking"),   // marking
                generateRandomTactic("mentality"), // mentality
                generateRandomTactic("midfielders"), // midfielders
                generateRandomTactic("offsideTrap"), // offsideTrap
                generateRandomTactic("pressure"), // pressure
                generateRandomTactic("tackling"), // tackling
                generateRandomTactic("tempo")  // tempo
        );
    }
    private String generateRandomTactic(String category) {
        Random random = new Random();

        switch (category) {
            case "attackers":
                return random.nextBoolean() ? "Many Forwards" : "Few Forwards";
            case "defenders":
                return random.nextBoolean() ? "Defensive Line High" : "Deep Defense";
            case "gamePlan":
                return random.nextBoolean() ? "Passing Game" : "Long Ball";
            case "marking":
                return random.nextBoolean() ? "Zonal Marking" : "Man Marking";
            case "mentality":
                return random.nextBoolean() ? "Attacking" : "Defensive";
            case "midfielders":
                return random.nextBoolean() ? "Playmaker Focused" : "Wing Play";
            case "offsideTrap":
                return random.nextBoolean() ? "Yes" : "No";
            case "pressure":
                return random.nextBoolean() ? "Close Down" : "Passive";
            case "tackling":
                return random.nextBoolean() ? "Aggressive" : "Conservative";
            case "tempo":
                return random.nextBoolean() ? "Fast Tempo" : "Slow Tempo";
            default:
                return "Balanced";
        }
    }
    private Slot generateAISlot() {
        return new Slot(
                "ai_slot",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new Date()
        );
    }
    public Mono<Void> finalizeMatchday(String instanceId, int matchday) {
    ZoneId zoneId = ZoneId.of("UTC");

    LocalDate today = LocalDate.now(zoneId);
    LocalDateTime startOfToday = today.atStartOfDay();
    LocalDateTime endOfToday = today.plusDays(1).atStartOfDay().minusNanos(1);

    Date from = Date.from(startOfToday.atZone(zoneId).toInstant());
    Date to = Date.from(endOfToday.atZone(zoneId).toInstant());

    System.out.println("🕒 [Simulation] Matchday window: " + from + " → " + to);

        return matchRepository.findAllByInstanceIdAndMatchday(instanceId, matchday)
                .filter(match -> !"completed".equals(match.getStatus()))
                .flatMap(match -> getSlotForMatch(match)
                        .flatMap(slotId -> finalizeMatch(instanceId, match.getMatchId(), slotId)))
                .collectList()
                .then(
                        leagueInstanceRepository.findFirstByInstanceId(instanceId)
                                .flatMap(instance -> {
                                    if (matchday < 38) {
                                        instance.setCurrentMatchday(matchday + 1);
                                    } else {
                                        instance.setCurrentMatchday(matchday);
                                    }
                                    return leagueInstanceRepository.save(instance)
                                            .doOnNext(updatedInstance ->
                                                    System.out.println("📈 Updated to matchday: " + updatedInstance.getCurrentMatchday()));
                                })
                )
                .then(
                        matchRepository.findAllByInstanceId(instanceId)
                                .map(Match::getMatchday)
                                .distinct()
                                .count()
                                .flatMap(totalMatchdays -> {
                                    if (matchday >= totalMatchdays) {
                                        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                                                .flatMap(inst -> {
                                                    inst.setStatus("completed");
                                                    return leagueInstanceRepository.save(inst)
                                                            .then(leagueService.finalizeLeague(instanceId))
                                                            .doOnSuccess(ignored -> {
                                                                System.out.println("🏁 League finalized: " + instanceId);
                                                                scheduleSlotResetTask(instanceId);
                                                            });
                                                });
                                    } else {
                                        return Mono.empty();
                                    }
                                })
                );

    }
    private Mono<String> getSlotForMatch(Match match) {
        return Mono.zip(
                getSlotForTeam(match.getTeamA(), match.getInstanceId()),
                getSlotForTeam(match.getTeamB(), match.getInstanceId())
        ).map(tuple -> {
            String slotA = tuple.getT1();
            String slotB = tuple.getT2();
            return (slotA != null && !slotA.isBlank()) ? slotA : slotB;
        });
    }

    public Mono<Void> finalizeMatch(String instanceId, String matchId, String slotId) {
        return matchRepository.findByInstanceIdAndMatchId(instanceId, matchId)
                .switchIfEmpty(Mono.error(new IllegalStateException("❌ Match not found: " + matchId + " in instance: " + instanceId)))
                .flatMap(match -> {
                    String status = match.getStatus();
                    if (!"pending".equals(status) && !"in_progress".equals(status)) {
                        System.err.println("❌ Skipping match " + matchId + ": unexpected status = " + status);
                        return Mono.empty();
                    }

                    System.out.println("🎮 Starting simulation for match: " + matchId + " [status: " + status + "]");

                    return initializeMatch(instanceId, matchId, slotId)
                            .flatMap(matchData -> simulateMatch(matchData)
                                    .flatMap(result -> {
                                        if (result == null) {
                                            System.err.println("❌ Simulation returned null result for match: " + matchId);
                                            return Mono.empty();
                                        }

                                        System.out.println("📦 Saving match result for match: " + matchId);

                                        return saveMatchResult(instanceId, matchId, result)
                                                .then(Mono.defer(() -> {
                                                    System.out.println("📊 Updating standings for match: " + matchId);
                                                    return leagueService.updateStandingsAfterMatch(instanceId, matchData.getMatch(), result);
                                                }))
                                                .then(Mono.fromRunnable(() -> {
                                                    System.out.println("📊 Broadcasting Match Update to : " + matchData.getMatch().getMatchId());
                                                    matchEventService.broadcastMatchUpdate(matchData.getMatch().getMatchId(), result);
                                                }));
                                    })
                            );
                })
                .doOnSuccess(unused -> System.out.println("✅ Finalized match: " + matchId))
                .doOnError(error -> System.err.println("❌ Failed to finalize match " + matchId + ": " + error.getMessage())).then();
    }
    public Mono<MatchResult> simulateMatch(MatchData matchData) {
        return Mono.zip(
                getSlotForTeam(matchData.getMatch().getTeamA(), matchData.getMatch().getInstanceId()),
                getSlotForTeam(matchData.getMatch().getTeamB(), matchData.getMatch().getInstanceId())
        ).flatMap(tuple -> {
            String slotA = tuple.getT1();
            String slotB = tuple.getT2();

            System.out.println("✅ Slot for TeamA: " + slotA + " | Slot for TeamB: " + slotB);

            return Mono.zip(
                    calculateScore(matchData.getTeamA(), matchData.getFormationA(), matchData.getTacticsA(), true, matchData.getTeamB()),
                    calculateScore(matchData.getTeamB(), matchData.getFormationB(), matchData.getTacticsB(), false, matchData.getTeamA())
            ).flatMap(tuple2 -> {
                int teamA_score = tuple2.getT1();
                int teamB_score = tuple2.getT2();

                return playerRepository.findByInstanceIdAndTeamId(matchData.getTeamA().getInstanceId(), matchData.getTeamA().getTeamId())
                        .collectList()
                        .zipWith(playerRepository.findByInstanceIdAndTeamId(matchData.getTeamB().getInstanceId(), matchData.getTeamB().getTeamId()).collectList())
                        .flatMap(playersTuple -> {
                            List<Player> teamAPlayers = playersTuple.getT1();
                            List<Player> teamBPlayers = playersTuple.getT2();

                            List<MatchEvent> teamAEvents = matchEventService.simulateEventsForTeam(matchData.getMatch().getMatchId(),
                                    matchData.getTeamA().getTeamId(), teamAPlayers, teamA_score);
                            List<MatchEvent> teamBEvents = matchEventService.simulateEventsForTeam(matchData.getMatch().getMatchId(),
                                    matchData.getTeamB().getTeamId(), teamBPlayers, teamB_score);

                            List<MatchEvent> allEvents = new ArrayList<>();
                            allEvents.addAll(teamAEvents);
                            allEvents.addAll(teamBEvents);

                            List<MatchEvent> sortedEvents = allEvents.stream()
                                    .sorted(Comparator.comparingInt(MatchEvent::getMinute))
                                    .toList();

                            sortedEvents.forEach(event -> {
                                event.setDescription(commentaryService.generateCommentary(event));
                            });

                            matchEventService.broadcastInProgressMatch(matchData.getMatch());

                            Flux.fromIterable(sortedEvents)
                                    .delayElements(Duration.ofSeconds(3))
                                    .publishOn(Schedulers.boundedElastic())
                                    .doOnNext(event -> {
                                        System.out.println("\ud83d\udce4 Sending event: " + event);
                                        matchEventService.broadcastMatchEvent(matchData.getMatch().getMatchId(), event);
                                    })
                                    .subscribe();

                            Map<String, Integer> rawScores = new HashMap<>();
                            for (MatchEvent e : allEvents) {
                                rawScores.computeIfAbsent(e.getPlayerId(), k -> 0);
                                int delta = switch (e.getType()) {
                                    case GOAL -> 3;
                                    case ASSIST -> 2;
                                    case SHOT -> 1;
                                    case FOUL -> -1;
                                    case YELLOW_CARD -> -2;
                                    case RED_CARD -> -3;
                                    case INJURY -> -1;
                                    case SUBSTITUTION -> 0;
                                    default -> 0;
                                };
                                rawScores.compute(e.getPlayerId(), (k, v) -> v + delta);
                            }

                            Map<String, Integer> playerRatings = new HashMap<>();
                            rawScores.forEach((key, raw) -> {
                                int rating = Math.min(10, Math.max(5, 6 + raw)); // Normalize
                                playerRatings.put(key, rating);
                            });

                            List<String> commentary = sortedEvents.stream()
                                    .map(e -> "[" + e.getMinute() + "] " + e.getDescription())
                                    .toList();

                            MatchReplay replay = new MatchReplay(
                                    matchData.getMatch().getMatchId(),
                                    sortedEvents,
                                    playerRatings
                            );

                            MatchResult result = new MatchResult(matchData.getMatch().getMatchId(), teamA_score, teamB_score, sortedEvents, commentary, playerRatings);

                            return matchReplayRepository.save(replay)
                                    .then(Mono.when(
                                            updateManagerActivity(matchData.getTeamA().getManagerId()),
                                            updateManagerActivity(matchData.getTeamB().getManagerId())
                                    ).thenReturn(result));
                        });
            });
        }).doOnSuccess(result -> {
            System.out.println("✅ Match simulated: " + matchData.getMatch().getMatchId() + " | Score: " +
                    result.getTeamA_score() + " - " + result.getTeamB_score());

            matchEventService.broadcastMatchUpdate(matchData.getMatch().getMatchId(), result);
        });
    }

    private Mono<String> getSlotForTeam(TeamDetails team, String instanceId) {
        if (team.getManagerId() == null || team.getManagerId().isEmpty()) {
            return Mono.just("ai_slot");
        }

        return managerRepository.findByManagerId(team.getManagerId())
                .map(manager -> {
                    if (manager.getManagerId() == null || manager.getSlots() == null) return null;

                    return manager.getSlots().values().stream()
                            .filter(slot ->
                                    instanceId.equals(slot.getInstanceId()) &&
                                            team.getTeamId().equals(slot.getTeamId()))
                            .map(Slot::getId)
                            .findFirst()
                            .orElse(null);
                })
                .defaultIfEmpty("ai_slot");
    }

    private Mono<Void> saveMatchResult(String instanceId, String matchId, MatchResult result) {
        return matchRepository.findByInstanceIdAndMatchId(instanceId, matchId)
                .flatMap(match -> {
                    if (result == null) {
                        System.err.println("❗Result is null for match " + matchId);
                    }
                    match.setResult(result);
                    match.setStatus("completed");
                    assert result != null;
                    match.setWinner(result.getTeamA_score() > result.getTeamB_score() ? match.getTeamA().getTeamId()
                            : result.getTeamB_score() > result.getTeamA_score() ? match.getTeamB().getTeamId() : "draw");
                    return matchRepository.save(match)
                            .doOnNext(saved -> System.out.println("✅ Saved match result for: " + saved.getMatchId()))
                            .then();
                });
    }

    private Mono<Integer> calculateScore(LeagueInstance.LeagueInstanceTeam team,
                                         Formation formation,
                                         Tactics tactics,
                                         boolean isHomeTeam,
                                         LeagueInstance.LeagueInstanceTeam opponentTeam) {
        return calculateTeamStrength(team.getInstanceId(), team.getTeamId())
                .zipWith(calculateTeamStrength(opponentTeam.getInstanceId(), opponentTeam.getTeamId()))
                .map(tuple -> {
                    int teamStrength = tuple.getT1();
                    int opponentStrength = tuple.getT2();

                    double tacticalBonus = (tactics != null ? tactics.calculateTacticalEffect() : 0);
                    double formationBonus = (formation != null ? formation.calculateFormationImpact() : 0);
                    double homeBonus = isHomeTeam ? 1.05 : 1.0;

                    double qualityFactor = Math.min(1.5, Math.max(0.5, teamStrength / 100.0));
                    double defenseFactor = Math.min(1.5, Math.max(0.5, opponentStrength / 100.0));

                    double scoreFactor = (qualityFactor + tacticalBonus + formationBonus) * homeBonus;
                    double cleanSheetChance = 1.0 - (scoreFactor / (scoreFactor + defenseFactor + 0.1));

                    if (Math.random() < cleanSheetChance * 0.25) return 0; // Clean sheet chance

                    double[] adjustedWeights = new double[WEIGHTS.length];
                    double total = 0;
                    for (int i = 0; i < WEIGHTS.length; i++) {
                        double weight = WEIGHTS[i] * (1 + scoreFactor * i * 0.15);
                        adjustedWeights[i] = weight;
                        total += weight;
                    }

                    for (int i = 0; i < adjustedWeights.length; i++) {
                        adjustedWeights[i] /= total;
                    }

                    System.out.printf("⚠️ scoreFactor=%.2f, weights=%s%n", scoreFactor, Arrays.toString(adjustedWeights));


                    return sampleFromDistribution(GOALS, adjustedWeights);
                });
    }

    private int sampleFromDistribution(int[] values, double[] weights) {
        double rand = Math.random();
        double cumulative = 0.0;

        double weightSum = Arrays.stream(weights).sum();
        if (weightSum <= 0) {
            return 0; // fallback to 0 goals
        }

        for (int i = 0; i < values.length; i++) {
            cumulative += weights[i];
            if (rand <= cumulative) {
                return values[i];
            }
        }

        return values[values.length - 1];
    }

    public Mono<Void> updateManagerActivity(String managerId) {
        if (managerId == null || managerId.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromFuture(() -> CompletableFuture.supplyAsync(() -> {
            try {
                ApiFuture<WriteResult> future = firestore.collection("managers")
                        .document(managerId)
                        .update("lastLogin", new Date());
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error updating manager activity", e);
            }
        })).then();
    }

    private Mono<Integer> calculateTeamStrength(String instanceId, String teamId) {
        return playerRepository.findByInstanceIdAndTeamId(instanceId, teamId)
                .map(p -> Optional.ofNullable(p.getCurrentStats()).map(Player.Stats::getOverallValue).orElse(0))
                .reduce(0, Integer::sum);
    }

    private boolean isHuman(Manager manager) {
        return manager.getManagerId() != null && !manager.getManagerId().isEmpty();
    }

    public Flux<Match> getMatchesForTeamOnMatchday(String instanceId, String teamId, int matchday) {
        return matchRepository.findAllByInstanceIdAndMatchday(instanceId, matchday)
                .filter(match ->
                        teamId.equals(match.getTeamA().getTeamId()) ||
                                teamId.equals(match.getTeamB().getTeamId())
                );
    }

    @SuppressWarnings("resource")
    private void scheduleSlotResetTask(String instanceId) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.schedule(() -> {
            resetManagerSlotsForInstance(instanceId)
                    .then(resetLeagueInstance(instanceId))
                    .doOnSuccess(v -> System.out.println("✅ Slots and league reset for instance: " + instanceId))
                    .doFinally(signalType -> executor.shutdown()) // 🔒 Shutdown after completion
                    .subscribe();
        }, 24, TimeUnit.HOURS);
    }


    public Mono<Void> resetManagerSlotsForInstance(String instanceId) {
        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                .flatMapMany(instance -> {
                    List<String> managerIds = instance.getReservedTeamsList().stream()
                            .map(LeagueInstance.ReservedTeam::getManagerId)
                            .distinct()
                            .toList();
                    return Flux.fromIterable(managerIds);
                })
                .flatMap(managerId -> {
                    DocumentReference managerRef = firestore.collection("managers").document(managerId);

                    return Mono.fromCallable(() -> managerRef.get().get()) // 👈 block safely here
                            .flatMap(snapshot -> {
                                if (!snapshot.exists()) return Mono.empty();

                                Map<String, Object> slots = (Map<String, Object>) snapshot.get("slots");
                                if (slots == null) return Mono.empty();

                                Map<String, Object> updates = new HashMap<>();
                                for (String slotKey : slots.keySet()) {
                                    Map<String, Object> slot = (Map<String, Object>) slots.get(slotKey);
                                    if (instanceId.equals(slot.get("instanceId"))) {
                                        Map<String, Object> update = new HashMap<>(slot);
                                        update.put("available", true);
                                        update.put("countryId", "");
                                        update.put("instanceId", "");
                                        update.put("leagueId", "");
                                        update.put("lineup", null);
                                        update.put("tactics", null);
                                        update.put("managerId", "");
                                        update.put("regionId", "");
                                        update.put("teamId", "");
                                        update.put("startDate", null);
                                        updates.put("slots." + slotKey, update);
                                    }
                                }

                                if (!updates.isEmpty()) {
                                    return Mono.fromCallable(() -> {
                                        managerRef.update(updates).get(); // blocking Firestore update
                                        return true;
                                    }).then();
                                } else {
                                    return Mono.empty();
                                }
                            });
                })
                .then();
    }

    public Mono<Void> resetLeagueInstance(String instanceId) {
        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                .flatMap(instance -> {
                    // Reset basic instance metadata
                    instance.setStatus("preparation");
                    instance.setCurrentMatchday(0);
                    instance.setReservedTeams(0);
                    instance.setAvailable(true);
                    instance.setReservedTeamsList(new ArrayList<>());
                    instance.setPreparationStart(new Date());

                    // Reset team slots
                    instance.getTeams().forEach(team -> {
                        team.setAvailable(true);
                        team.setManagerId(null);
                        team.setManagerName(null);
                    });

                    instance.getStandings().forEach(standing -> {
                        standing.setManagerId("");
                        standing.setManagerName("");
                        standing.setDraws(0);
                        standing.setGoalsAgainst(0);
                        standing.setGoalsFor(0);
                        standing.setLosses(0);
                        standing.setMatchesPlayed(0);
                        standing.setPoints(0);
                        standing.setGoalDifference(0);
                        standing.setWins(0);
                    });

                    Mono<Void> clearTransfers = leagueService.clearTransfers(instanceId);

                    Mono<Void> deleteMatches = matchRepository.deleteAllByInstanceId(instanceId);
                    Mono<Void> deleteReplays = matchReplayRepository.deleteAllByMatchIdRegex("^" + instanceId + "_match_");
                    Mono<Void> deleteOffers = transferOfferRepository.deleteAllByInstanceId(instanceId);

                    return Mono.when(
                            leagueInstanceRepository.save(instance),
                            clearTransfers,
                            deleteMatches,
                            deleteReplays,
                            deleteOffers
                    ).then();
                });
    }



}
