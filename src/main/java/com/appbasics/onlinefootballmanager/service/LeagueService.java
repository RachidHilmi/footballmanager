    package com.appbasics.onlinefootballmanager.service;
    
    import com.appbasics.onlinefootballmanager.model.*;
    import com.appbasics.onlinefootballmanager.repository.firestore.*;
    import com.appbasics.onlinefootballmanager.repository.mongo.*;
    import com.appbasics.onlinefootballmanager.util.ApiFutureUtils;
    import com.appbasics.onlinefootballmanager.util.FirestoreUtils;
    import com.google.api.core.ApiFuture;
    import com.google.cloud.Timestamp;
    import com.google.cloud.firestore.*;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.stereotype.Service;
    import reactor.core.publisher.Mono;
    
    import java.text.DateFormat;
    import java.text.SimpleDateFormat;
    import java.util.*;
    import java.util.stream.Collectors;
    
    @Service
    public class LeagueService {

        @Autowired
        private StandingsRepository standingsRepository;
        @Autowired
        private MatchRepository matchRepository;
        @Autowired
        private ManagerRepository managerRepository;
        @Autowired
        private PlayerRepository playerRepository;
        @Autowired
        private LeagueTemplateRepository leagueTemplateRepository;
        @Autowired
        private Firestore firestore;
        @Autowired
        private RewardService rewardService;
        @Autowired
        private LeagueInstanceRepository leagueInstanceRepository;
        @Autowired
        private TransferRecordRepository transferRecordRepository;
    
        List<CardInstance> grantedCards = new ArrayList<>();
    
    
        public Mono<Void> awardCombinedManagerStats(String managerId, int coins,
                                                    int points, int leaguesWon, int objectivesCompleted,
                                                    int dominationPoints) {
            Map<String, Object> updates = new HashMap<>();
            if (coins > 0) updates.put("coins", FieldValue.increment(coins));
            if (points > 0) updates.put("managerPoints", FieldValue.increment(points));
            if (leaguesWon > 0) updates.put("leaguesWon", FieldValue.increment(leaguesWon));
            if (objectivesCompleted > 0) updates.put("objectivesCompleted", FieldValue.increment(objectivesCompleted));
            if (dominationPoints > 0) updates.put("dominationPoints", FieldValue.increment(dominationPoints));
    
    
            return Mono.fromFuture(ApiFutureUtils.toCompletableFuture(
                    firestore.collection("managers").document(managerId).update(updates)
            )).then();
        }


        public Mono<Void> finalizeLeague(String instanceId) {
            return Mono.zip(
                    standingsRepository.findByInstanceIdOrderByPointsDescGoalDifferenceDesc(instanceId).collectList(),
                    leagueInstanceRepository.findFirstByInstanceId(instanceId)
            ).flatMap(tuple -> {
                List<Standing> standings = tuple.getT1();
                LeagueInstance instance = tuple.getT2();
                String leagueId = instance.getLeagueId();
                String templateId = instance.getTemplateId();

                standings.sort(Comparator.comparingInt(Standing::getPoints)
                        .thenComparingInt(Standing::getGoalDifference)
                        .reversed());

                List<Mono<Void>> rewardMonos = new ArrayList<>();

                Standing leagueWinner = standings.get(0);
                if (leagueWinner.getManagerId() != null && !leagueWinner.getManagerId().isEmpty()) {
                    int position = 1;
                    rewardMonos.add(
                            awardLeagueWinnerRewards(leagueWinner.getManagerId(), instanceId, position, leagueWinner.getTeamId())
                                    .then(rewardService.grantCards(leagueWinner.getManagerId(), "RARE", 2, "tournament_end", grantedCards))
                    );
                }

                return leagueTemplateRepository.findById(templateId)
                        .flatMap(template -> {
                            Map<String, String> teamIdToObjective = template.getTeams().stream()
                                    .collect(Collectors.toMap(
                                            LeagueTemplate.LeagueTemplateTeam::getTeamId,
                                            LeagueTemplate.LeagueTemplateTeam::getTeamObjective
                                    ));

                            for (int i = 1; i < standings.size(); i++) {
                                Standing standing = standings.get(i);
                                String managerId = standing.getManagerId();
                                if (managerId != null && !managerId.isEmpty()) {
                                    int position = i + 1;
                                    String teamObjective = teamIdToObjective.getOrDefault(standing.getTeamId(), "Objective: 99");
                                    int objectivePosition = parseObjectiveString(teamObjective);

                                    boolean achieved = position <= objectivePosition;
                                    rewardMonos.add(
                                            achieved
                                                    ? awardAchievedObjectiveRewards(managerId, instanceId, position, true, standing.getTeamId())
                                                    : awardStandardRewards(managerId, position, instanceId)
                                    );
                                }
                            }

                            Mono<Void> rewards = Mono.when(rewardMonos);
                            Mono<Void> restorePlayers = restoreTransferredPlayers(instanceId);
                            Mono<Void> resetStats = resetPlayerStatsToBase(instanceId);
                            return Mono.when(rewards, restorePlayers, resetStats);
                        });
            });
        }


        private Mono<Void> processManagerRewards(Standing standing, String leagueId, int position) {
            String managerId = standing.getManagerId();
            String teamId = standing.getTeamId();
    
            if (managerId == null || managerId.isEmpty()) {
                System.err.println("Skipping AI team at position " + position + ", teamId: " + standing.getTeamId());
                return Mono.empty(); // Skip AI teams
            }
    
            // Ensure we don't reward twice
            System.err.println("Processing rewards for manager: " + managerId + " at position: " + position);
    
            // Fetch team data to get team objectives
            Mono<DocumentSnapshot> teamDocMono = Mono.fromCallable(() -> {
                try {
                    return firestore.collection("teams")
                            .document(leagueId + standing.getTeamId())  // Construct teamId as leagueId + teamId
                            .get()
                            .get();  // Blocking call to get the document snapshot
                } catch (Exception e) {
                    throw new RuntimeException("Error fetching team from teams collection", e);
                }
            });
    
            return teamDocMono.flatMap(teamDoc -> {
                if (!teamDoc.exists()) {
                    System.err.println("Team not found in teams collection for teamId: " + standing.getTeamId());
                    return Mono.empty();
                }
    
                Map<String, Object> teamData = teamDoc.getData();
                String teamObjectiveStr = (String) teamData.get("teamObjective");
                int teamObjective = parseObjectiveString(teamObjectiveStr);
    
                boolean achievedObjective = position <= teamObjective;
                boolean isLeagueWinner = position == 1;
    
                // If the team is the league winner and is human-managed
                if (isLeagueWinner) {
                    return awardLeagueWinnerRewards(managerId, leagueId, position, teamId);
                }
    
                // If the team achieved their objective
                if (achievedObjective) {
                    return awardAchievedObjectiveRewards(managerId, leagueId, position, true, teamId);
                }
    
                // If neither league winner nor achieved objective, grant 10 coins and manager points
                return awardStandardRewards(managerId, position, leagueId);
            });
        }

        private Mono<Void> awardLeagueWinnerRewards(String managerId, String instanceId, int position, String teamId) {
            return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                    .flatMap(instance -> {
                        String leagueId = instance.getLeagueId();
                        String countryId = instance.getCountryId();

                        Mono<Void> trophiesMono = awardTrophies(managerId, instanceId, position, true, true);
                        Mono<Void> coinsMono = awardCoins(managerId, 250);
                        Mono<Integer> pointsMono = calculateManagerPoints(managerId, instanceId);

                        Mono<Void> updateManagerPointsMono = pointsMono.flatMap(points ->
                                Mono.fromRunnable(() -> {
                                    try {
                                        firestore.collection("managers").document(managerId)
                                                .update("managerPoints", FieldValue.increment(points));
                                    } catch (Exception e) {
                                        System.err.println("Error updating manager points: " + e.getMessage());
                                    }
                                })
                        ).then().onErrorResume(e -> {
                            System.err.println("Error updating manager points: " + e.getMessage());
                            return Mono.empty();
                        });

                        Mono<Void> dominationMono = handleDominationUpdates(managerId, countryId, true, true);

                        return Mono.when(trophiesMono, coinsMono, updateManagerPointsMono, dominationMono);
                    });
        }

        private Mono<Void> awardAchievedObjectiveRewards(String managerId, String instanceId, int position, boolean achievedObjective, String teamId) {
            return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                    .flatMap(instance -> {
                        String leagueId = instance.getLeagueId();
                        String countryId = instance.getCountryId();

                        Mono<Void> trophiesMono = awardTrophies(managerId, instanceId, position, false, true);
                        int coins = calculateCoins(position, achievedObjective);
                        Mono<Void> coinsMono = awardCoins(managerId, coins);
                        Mono<Integer> pointsMono = calculateManagerPoints(managerId, instanceId);

                        Mono<Void> updateManagerPointsMono = pointsMono.flatMap(points ->
                                Mono.fromRunnable(() -> {
                                    try {
                                        firestore.collection("managers").document(managerId)
                                                .update("managerPoints", FieldValue.increment(points));
                                    } catch (Exception e) {
                                        System.err.println("Error updating manager points: " + e.getMessage());
                                    }
                                })
                        ).then().onErrorResume(e -> {
                            System.err.println("Error updating manager points: " + e.getMessage());
                            return Mono.empty();
                        });

                        Mono<Void> dominationMono = handleDominationUpdates(managerId, countryId, false, true);

                        return Mono.when(trophiesMono, coinsMono, updateManagerPointsMono, dominationMono);
                    });
        }

        private Mono<Void> awardStandardRewards(String managerId, int position, String instanceId) {
            return Mono.defer(() -> {
                Mono<Void> coinsMono = awardCoins(managerId, 10);
                Mono<Integer> pointsMono = calculateManagerPoints(managerId, instanceId);

                Mono<Void> updateManagerPointsMono = pointsMono.flatMap(points ->
                        Mono.fromRunnable(() -> {
                            try {
                                firestore.collection("managers").document(managerId)
                                        .update("managerPoints", FieldValue.increment(points));
                            } catch (Exception e) {
                                System.err.println("Error updating manager points: " + e.getMessage());
                            }
                        })
                ).then().onErrorResume(e -> {
                    System.err.println("Error updating manager points: " + e.getMessage());
                    return Mono.empty();
                });

                return Mono.when(coinsMono, updateManagerPointsMono);
            });
        }

        private Mono<Void> awardCoins(String managerId, int amount) {
            return Mono.fromFuture(ApiFutureUtils.toCompletableFuture(
                    firestore.collection("managers").document(managerId)
                            .update("coins", FieldValue.increment(amount))
            )).onErrorResume(e -> {
                System.err.println("Error awarding coins: " + e.getMessage());
                return Mono.empty();
            }).then();
        }

        private Mono<Void> awardTrophies(String managerId, String leagueId, int position, boolean wonLeague, boolean achievedObjective) {
            List<String> objectives = new ArrayList<>();
            List<String> type = new ArrayList<>();
    
            if (wonLeague) {
                objectives.add("win_league");
                objectives.add("reach_objective");
                type.add("league");
            } else if (achievedObjective) {
                objectives.add("reach_objective");
                type.add("objective");
            }
    
            String trophyId = leagueId + (wonLeague ? "_champion" : "_objective_" + position);
            String trophyName = (wonLeague ? "League Champion Trophy" : "Objective Reached Trophy") + " - " + leagueId;
    
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            String dateWon = df.format(new Date());
    
            Trophy trophy = new Trophy(
                    trophyId,
                    trophyName,
                    type,
                    dateWon,
                    objectives
            );
    
            DocumentReference trophyRef = firestore.collection("managers")
                    .document(managerId)
                    .collection("trophy")
                    .document(trophy.getId());
    
            DocumentReference managerRef = firestore.collection("managers").document(managerId);
    
            // Determine what to increment
            Map<String, Object> increments = new HashMap<>();
            if (wonLeague) {
                increments.put("leaguesWon", FieldValue.increment(1));
            }
            if (achievedObjective) {
                increments.put("objectivesCompleted", FieldValue.increment(1));
            }
    
            ApiFuture<WriteResult> addTrophyFuture = trophyRef.set(trophy);
            ApiFuture<WriteResult> updateCountersFuture = managerRef.update(increments);
    
            return Mono.when(
                    FirestoreUtils.monoFromApiFuture(addTrophyFuture),
                    FirestoreUtils.monoFromApiFuture(updateCountersFuture)
            ).then();
        }
    
        private static final Map<Integer, Integer> coinsByPosition;
        static {
            coinsByPosition = new HashMap<>();
            coinsByPosition.put(1, 250);
            coinsByPosition.put(2, 200);
            coinsByPosition.put(3, 180);
            coinsByPosition.put(4, 160);
            coinsByPosition.put(5, 140);
            coinsByPosition.put(6, 120);
            coinsByPosition.put(7, 100);
            coinsByPosition.put(8, 80);
            coinsByPosition.put(9, 60);
            coinsByPosition.put(10, 50);
            coinsByPosition.put(11, 45);
            coinsByPosition.put(12, 40);
            coinsByPosition.put(13, 35);
            coinsByPosition.put(14, 30);
            coinsByPosition.put(15, 25);
            coinsByPosition.put(16, 15);
        }
    
        private int calculateCoins(int position, boolean achievedObjective) {
    
            if (achievedObjective) {
                return coinsByPosition.getOrDefault(position, 10);
            }
            return 10;
        }
    
    
        private Mono<Void> handleDominationUpdates(String managerId, String countryId, boolean currentLeagueWon, boolean currentObjectiveWon) {
            DocumentReference countryDocRef = firestore.collection("managers")
                    .document(managerId)
                    .collection("dominatedCountries")
                    .document(countryId);
    
            return Mono.fromFuture(ApiFutureUtils.toCompletableFuture(countryDocRef.get()))
                    .flatMap(snapshot -> {
                        boolean prevLeagueWon = snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("leagueWon"));
                        boolean prevObjectiveWon = snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("objectiveWon"));
    
                        // Completely skip if already achieved everything previously.
                        if (prevLeagueWon && prevObjectiveWon) return Mono.empty();
    
                        Map<String, Object> updates = new HashMap<>();
                        boolean incrementDomination = false;
    
                        // Upgrade from objectiveWon=true to leagueWon=true only.
                        if (!prevLeagueWon && prevObjectiveWon && currentLeagueWon) {
                            updates.put("leagueWon", true);
                            updates.put("updatedAt", Timestamp.now());
                        }
    
                        // First-time achieving objective or league
                        if (!prevObjectiveWon && currentObjectiveWon) {
                            updates.put("objectiveWon", true);
                            updates.put("leagueWon", currentLeagueWon);
                            updates.put("updatedAt", Timestamp.now());
                            incrementDomination = true; // ONLY increment if first-time objective achieved
                        }
    
                        Mono<Void> updateCountryMono = updates.isEmpty()
                                ? Mono.empty()
                                : Mono.fromFuture(ApiFutureUtils.toCompletableFuture(
                                countryDocRef.set(updates, SetOptions.merge())
                        )).then();
    
                        Mono<Void> incrementDominationMono = incrementDomination
                                ? incrementDominationPoints(managerId) : Mono.empty();
    
                        return Mono.when(updateCountryMono, incrementDominationMono);
                    }).onErrorResume(e -> {
                        System.err.println("Error updating domination: " + e);
                        return Mono.empty();
                    });
        }
    
        private Mono<Void> incrementDominationPoints(String managerId) {
            ApiFuture<WriteResult> apiFuture = firestore.collection("managers")
                    .document(managerId)
                    .update("dominationPoints", FieldValue.increment(1));
    
            return Mono.fromFuture(ApiFutureUtils.toCompletableFuture(apiFuture)).then();
        }

        private Mono<Integer> calculateManagerPoints(String managerId, String instanceId) {
            return Mono.zip(
                    matchRepository.findAllByInstanceId(instanceId)
                            .filter(match -> "completed".equals(match.getStatus()) &&
                                    (managerId.equals(match.getTeamA().getManagerId()) ||
                                            managerId.equals(match.getTeamB().getManagerId())))
                            .collectList(),
                    leagueInstanceRepository.findFirstByInstanceId(instanceId)
            ).flatMap(tuple -> {
                List<Match> matches = tuple.getT1();
                LeagueInstance instance = tuple.getT2();
                String templateId = instance.getTemplateId();

                return leagueTemplateRepository.findById(templateId)
                        .map(template -> {
                            Map<String, String> teamIdToObjective = template.getTeams().stream()
                                    .collect(Collectors.toMap(
                                            LeagueTemplate.LeagueTemplateTeam::getTeamId,
                                            LeagueTemplate.LeagueTemplateTeam::getTeamObjective
                                    ));

                            int totalPoints = matches.stream()
                                    .mapToInt(match -> {

                                boolean isTeamA = managerId.equals(match.getTeamA().getManagerId());
                                int teamScore = isTeamA ? match.getResult().getTeamA_score() : match.getResult().getTeamB_score();
                                int opponentScore = isTeamA ? match.getResult().getTeamB_score() : match.getResult().getTeamA_score();

                                String teamId = isTeamA ? match.getTeamA().getTeamId() : match.getTeamB().getTeamId();
                                String opponentId = isTeamA ? match.getTeamB().getTeamId() : match.getTeamA().getTeamId();

                                int teamObjective = parseObjectiveString(teamIdToObjective.getOrDefault(teamId, "Objective: 99"));
                                int opponentObjective = parseObjectiveString(teamIdToObjective.getOrDefault(opponentId, "Objective: 99"));
                                int diff = opponentObjective - teamObjective;

                                if (teamScore > opponentScore) {
                                    if (diff >= 15) return 500;
                                    if (diff <= -15) return 100;
                                    return Math.max(100, Math.min(500, 250 + diff * 15));
                                }
                                return (teamScore == opponentScore) ? 100 : 0;
                            }).sum();

                            return Math.min(totalPoints, 10000);
                        });
            }).defaultIfEmpty(0);
        }

        private int parseObjectiveString(String objectiveStr) {
            try {
                if (objectiveStr == null || objectiveStr.isEmpty()) return Integer.MAX_VALUE;
                String cleaned = objectiveStr.replaceAll("Objective:\\s*", "").trim();
                return Integer.parseInt(cleaned);
            } catch (Exception e) {
                System.err.println("Failed to parse objective string: " + objectiveStr);
                return Integer.MAX_VALUE;
            }
        }

        public Mono<Void> updateStandingsAfterMatch(String instanceId, Match match, MatchResult result) {
            if (result == null) {
                System.err.println("❌ No result for match: " + match.getMatchId());
                return Mono.empty();
            }

            String teamAId = match.getTeamA().getTeamId();
            String teamBId = match.getTeamB().getTeamId();
            int scoreA = result.getTeamA_score();
            int scoreB = result.getTeamB_score();

            Mono<Standing> updatedTeamA = standingsRepository.findByInstanceIdAndTeamId(instanceId, teamAId)
                    .flatMap(standing -> {
                        System.out.println("✅ Updating standing for teamA: " + teamAId);
                        updateTeamStanding(standing, scoreA, scoreB);
                        return standingsRepository.save(standing);
                    })
                    .switchIfEmpty(Mono.fromRunnable(() -> {
                        System.err.println("❌ No standing found for teamA: " + teamAId + " in instanceId: " + instanceId);
                    }).then(Mono.empty()));

            Mono<Standing> updatedTeamB = standingsRepository.findByInstanceIdAndTeamId(instanceId, teamBId)
                    .flatMap(standing -> {
                        System.out.println("✅ Updating standing for teamB: " + teamBId);
                        updateTeamStanding(standing, scoreB, scoreA);
                        return standingsRepository.save(standing);
                    })
                    .switchIfEmpty(Mono.fromRunnable(() -> {
                        System.err.println("❌ No standing found for teamB: " + teamBId + " in instanceId: " + instanceId);
                    }).then(Mono.empty()));

            return Mono.zip(updatedTeamA, updatedTeamB)
                    .flatMap(tuple -> {
                        Standing standingA = tuple.getT1();
                        Standing standingB = tuple.getT2();

                        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                                .flatMap(instance -> {
                                    List<Standing> current = instance.getStandings();
                                    if (current != null) {
                                        // Replace old standings with updated ones
                                        current.removeIf(s -> s.getTeamId().equals(teamAId) || s.getTeamId().equals(teamBId));
                                        current.add(standingA);
                                        current.add(standingB);
                                        instance.setStandings(current);
                                    }
                                    return leagueInstanceRepository.save(instance);
                                });
                    })
                    .then();
        }

        private void updateTeamStanding(Standing standing, int goalsFor, int goalsAgainst) {
            standing.setMatchesPlayed(standing.getMatchesPlayed() + 1);
            standing.setGoalsFor(standing.getGoalsFor() + goalsFor);
            standing.setGoalsAgainst(standing.getGoalsAgainst() + goalsAgainst);
            standing.setGoalDifference(standing.getGoalsFor() - standing.getGoalsAgainst());

            if (goalsFor > goalsAgainst) {
                standing.setWins(standing.getWins() + 1);
                standing.setPoints(standing.getPoints() + 3);
            } else if (goalsFor == goalsAgainst) {
                standing.setDraws(standing.getDraws() + 1);
                standing.setPoints(standing.getPoints() + 1);
            } else {
                standing.setLosses(standing.getLosses() + 1);
            }
        }

        public Mono<Void> initializeStandingsIfEmpty(String instanceId) {
            return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                    .flatMap(instance -> {
                        if (instance.getStandings() != null && !instance.getStandings().isEmpty()) {
                            return Mono.empty();
                        }

                        List<LeagueInstance.LeagueInstanceTeam> teams = instance.getTeams();
                        if (teams == null || teams.isEmpty()) {
                            return Mono.error(new IllegalStateException("No teams to initialize standings."));
                        }

                        List<Standing> newStandings = teams.stream()
                                .sorted(Comparator.comparing(LeagueInstance.LeagueInstanceTeam::getTeamId, String.CASE_INSENSITIVE_ORDER))
                                .map(team -> {
                                    Standing standing = new Standing();
                                    standing.set_id(instanceId + "_" + team.getTeamId());
                                    standing.setTeamId(team.getTeamId());
                                    standing.setTeamName(team.getTeamName());
                                    standing.setManagerId(team.getManagerId());
                                    standing.setManagerName(team.getManagerName());
                                    standing.setLeagueId(instance.getLeagueId());
                                    standing.setInstanceId(instanceId);
                                    // Stats
                                    standing.setPoints(0);
                                    standing.setWins(0);
                                    standing.setLosses(0);
                                    standing.setDraws(0);
                                    standing.setGoalsFor(0);
                                    standing.setGoalsAgainst(0);
                                    standing.setGoalDifference(0);
                                    standing.setMatchesPlayed(0);
                                    return standing;
                                })
                                .collect(Collectors.toList());

                        instance.setStandings(
                                newStandings.stream()
                                        .sorted(Comparator.comparing(Standing::getTeamId, String.CASE_INSENSITIVE_ORDER))
                                        .collect(Collectors.toList())
                        );

                        return standingsRepository.saveAll(newStandings)
                                .then(leagueInstanceRepository.save(instance))
                                .then();
                    });
        }

        public int calculateNewRanking(Standing standing) {
            return (standing.getWins() * 3) + (standing.getDraws()) +
                    (standing.getGoalDifference() / 2);
        }
    
        private Mono<Void> resetPlayerStatsToBase(String instanceId) {
            return playerRepository.findByInstanceId(instanceId)
                    .flatMap(player -> {
                        Player.Stats baseStats = player.getBaseStats();
                        if (baseStats == null) {
                            System.err.println("⚠️ Missing baseStats for player: " + player.getPlayerId());
                            return Mono.empty();
                        }
    
                        // Clone baseStats into currentStats
                        Player.Stats newStats = new Player.Stats();
                        newStats.setMarketValue(baseStats.getMarketValue());
                        newStats.setAttackValue(baseStats.getAttackValue());
                        newStats.setDefenseValue(baseStats.getDefenseValue());
                        newStats.setOverallValue(baseStats.getOverallValue());
                        newStats.setMoralLevel(baseStats.getMoralLevel());
                        newStats.setFitnessLevel(baseStats.getFitnessLevel());
    
                        newStats.setExpectedBoost(0);
                        newStats.setPendingBoost(0);
                        newStats.setLastTrainedAt(0);
    
                        player.setCurrentStats(newStats);
                        player.setStatus("available");
    
                        return playerRepository.save(player);
                    })
                    .then();
        }

        public Mono<Void> restoreTransferredPlayers(String instanceId) {
            return playerRepository.findByInstanceId(instanceId)
                    .filter(player -> player.getOriginalTeamId() != null)
                    .flatMap(player -> {
                        player.setTeamId(player.getOriginalTeamId());
                        player.setPlayerId(player.get_id());
                        player.setOriginalTeamId(null);
                        return playerRepository.save(player);
                    })
                    .then();
        }

        public Mono<Void> clearTransfers(String instanceId) {
            return transferRecordRepository.deleteAllByNewInstanceId(instanceId)
                    .doOnSuccess(v -> System.out.println("✅ Cleared transfers for: " + instanceId));
        }
    }
    
