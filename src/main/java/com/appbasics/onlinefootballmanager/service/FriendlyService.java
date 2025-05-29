package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.*;
import com.appbasics.onlinefootballmanager.repository.firestore.ManagerRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.FriendlyMatchRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class FriendlyService {

    @Autowired
    private MatchService matchService;
    @Autowired private LeagueInstanceRepository leagueInstanceRepository;
    @Autowired private ManagerRepository managerRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private FriendlyMatchRepository friendlyMatchRepository;
    @Autowired private PlayerService playerService;

    public Mono<MatchResult> simulateFriendlyMatch(String instanceId, String initiatorManagerId, String targetTeamId) {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Date start = Date.from(today.atStartOfDay(ZoneId.of("UTC")).toInstant());
        Date end = Date.from(today.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant());

        return managerRepository.findByManagerId(initiatorManagerId)
                .flatMap(manager -> {
                    if (manager.getCoins() < 4) {
                        return Mono.error(new RuntimeException("Not enough coins to play a friendly match."));
                    }

                    manager.setCoins(manager.getCoins() - 4);
                    return managerRepository.save(manager);
                })
                .then(leagueInstanceRepository.findFirstByInstanceId(instanceId))
                .flatMap(instance -> {
                    Optional<LeagueInstance.LeagueInstanceTeam> teamAOpt = instance.getTeams().stream()
                            .filter(t -> initiatorManagerId.equals(t.getManagerId()))
                            .findFirst();

                    Optional<LeagueInstance.LeagueInstanceTeam> teamBOpt = instance.getTeams().stream()
                            .filter(t -> t.getTeamId().equals(targetTeamId))
                            .findFirst();

                    if (teamAOpt.isEmpty() || teamBOpt.isEmpty()) {
                        return Mono.error(new IllegalStateException("Teams not found in league instance."));
                    }

                    LeagueInstance.LeagueInstanceTeam teamA = teamAOpt.get();
                    LeagueInstance.LeagueInstanceTeam teamB = teamBOpt.get();

                    return friendlyMatchRepository.findByInitiatorManagerIdAndTargetTeamIdAndInstanceIdAndPlayedAtBetween(
                                    initiatorManagerId, targetTeamId, instanceId, start, end
                            ).hasElements()
                            .flatMap(alreadyPlayed -> {
                                if (alreadyPlayed) {
                                    return Mono.error(new IllegalStateException("Already played a friendly against this team today."));
                                }

                                Match match = new Match();
                                match.setMatchId("friendly_" + UUID.randomUUID());
                                match.setInstanceId(instanceId);
                                match.setTeamA(new TeamDetails(teamA.getManagerId(), teamA.getManagerName(), teamA.getTeamId(), teamA.getTeamName()));
                                match.setTeamB(new TeamDetails(teamB.getManagerId(), teamB.getManagerName(), teamB.getTeamId(), teamB.getTeamName()));
                                match.setStatus("completed");
                                match.setMatchDate(new Date());

                                Mono<Slot> slotAMono = managerRepository.findByManagerId(teamA.getManagerId())
                                        .map(manager -> manager.getSlots().values().stream()
                                                .filter(slot -> teamA.getTeamId().equals(slot.getTeamId()))
                                                .findFirst().orElse(new Slot()))
                                        .defaultIfEmpty(new Slot());

                                Mono<Slot> slotBMono = managerRepository.findByManagerId(teamB.getManagerId())
                                        .map(manager -> manager.getSlots().values().stream()
                                                .filter(slot -> teamB.getTeamId().equals(slot.getTeamId()))
                                                .findFirst().orElse(new Slot()))
                                        .defaultIfEmpty(new Slot());

                                return Mono.zip(slotAMono, slotBMono)
                                        .flatMap(tuple -> {
                                            Slot slotA = tuple.getT1();
                                            Slot slotB = tuple.getT2();

                                            Formation formationA = Optional.ofNullable(slotA.getLineup()).orElse(null);
                                            Formation formationB = Optional.ofNullable(slotB.getLineup()).orElse(null);

                                            Tactics tacticsA = Optional.ofNullable(slotA.getTactics()).orElse(matchService.generateAITactics());
                                            Tactics tacticsB = Optional.ofNullable(slotB.getTactics()).orElse(matchService.generateAITactics());

                                            MatchData matchData = new MatchData(
                                                    match, teamA, teamB,
                                                    formationA, formationB,
                                                    tacticsA, tacticsB
                                            );

                                            return matchService.simulateMatch(matchData)
                                                    .flatMap(result ->
                                                            applyTrainingBoost(instanceId, teamA.getTeamId())
                                                                    .then(applyTrainingBoost(instanceId, teamB.getTeamId()))
                                                                    .then(saveFriendlyMatch(instanceId, initiatorManagerId, teamA, teamB, result))
                                                                    .thenReturn(result)
                                                    );
                                        });
                            });
                });
    }



    private Mono<FriendlyMatch> saveFriendlyMatch(String instanceId,
                                                  String initiatorManagerId,
                                                  LeagueInstance.LeagueInstanceTeam teamA,
                                                  LeagueInstance.LeagueInstanceTeam teamB,
                                                  MatchResult result) {

        FriendlyMatch fm = new FriendlyMatch(
                UUID.randomUUID().toString(),
                instanceId,
                initiatorManagerId,
                teamA.getTeamId(),
                teamB.getManagerId(),
                teamB.getTeamId(),
                new Date(),
                result
        );

        return friendlyMatchRepository.save(fm);
    }

    private Mono<Void> applyTrainingBoost(String instanceId, String teamId) {
        return playerRepository.findByInstanceIdAndTeamId(instanceId, teamId)
                .flatMap(player -> {
                    if (player.getCurrentStats() == null) return Mono.empty();

                    Player.Stats stats = player.getCurrentStats();
                    double boost = playerService.calculateBaseBoost(player.getAge());

                    if (player.getAge() <= 22 && Math.random() < 0.15) {
                        boost += 1;
                    }

                    stats.setExpectedBoost(stats.getExpectedBoost() + boost);
                    player.setCurrentStats(stats);

                    return playerRepository.save(player);
                })
                .then();
    }

    public Flux<FriendlyMatch> getFriendliesPlayedToday(String instanceId, String managerId) {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Date start = Date.from(today.atStartOfDay(ZoneId.of("UTC")).toInstant());
        Date end = Date.from(today.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant());

        return friendlyMatchRepository.findByInitiatorManagerIdAndInstanceIdAndPlayedAtBetween(
                managerId, instanceId, start, end
        );
    }
}
