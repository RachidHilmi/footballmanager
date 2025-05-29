package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.LeagueInstance;
import com.appbasics.onlinefootballmanager.model.Match;
import com.appbasics.onlinefootballmanager.model.Player;
import com.appbasics.onlinefootballmanager.model.TeamDetails;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueTemplateRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.MatchRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class LeagueSelectionService {


    private final LeagueInstanceRepository leagueInstanceRepository;
    private final LeagueTemplateRepository leagueTemplateRepository;
    private final PlayerRepository playerRepository;

    @Autowired
    public LeagueSelectionService(LeagueInstanceRepository leagueInstanceRepository,
                                  LeagueTemplateRepository leagueTemplateRepository, PlayerRepository playerRepository) {
        this.leagueInstanceRepository = leagueInstanceRepository;
        this.leagueTemplateRepository = leagueTemplateRepository;
        this.playerRepository = playerRepository;
    }

    public Mono<LeagueInstance> selectOrCreateLeagueInstance(String regionId, String baseLeagueId, String managerId) {
        return leagueInstanceRepository
                .findByLeagueId(baseLeagueId)
                .collectList()
                .flatMap(instances -> {
                    // Check if manager already joined any instance
                    Set<String> joinedInstanceIds = instances.stream()
                            .filter(instance -> instance.getTeams().stream()
                                    .anyMatch(team -> managerId.equals(team.getManagerId())))
                            .map(LeagueInstance::getInstanceId)
                            .collect(Collectors.toSet());

                    // Find an available instance the manager hasn't joined
                    Optional<LeagueInstance> availableInstanceOpt = instances.stream()
                            .filter(instance -> !joinedInstanceIds.contains(instance.getInstanceId()))
                            .filter(instance -> instance.getTeams().stream().anyMatch(LeagueInstance.LeagueInstanceTeam::isAvailable))
                            .findFirst();

                    if (availableInstanceOpt.isPresent()) {
                        return Mono.just(availableInstanceOpt.get());
                    } else {
                        return createNewLeagueInstanceWithClones(regionId, baseLeagueId);
                    }
                });
    }

    public Mono<LeagueInstance> createNewLeagueInstanceWithClones(String regionId, String baseLeagueId) {
        String templateId = regionId + "_" + baseLeagueId;

        return leagueTemplateRepository.findById(templateId)
                .switchIfEmpty(Mono.error(new RuntimeException("Template not found: " + templateId)))
                .flatMap(template -> {
                    String instanceIdPrefix = templateId + "_instance_";

                    return leagueInstanceRepository.findByTemplateId(templateId)
                            .collectList()
                            .flatMap(existingInstances -> {
                                int nextInstanceNumber = existingInstances.size() + 1;
                                String newInstanceId = instanceIdPrefix + String.format("%03d", nextInstanceNumber);

                                List<String> templatePlayerIds = template.getTeams().stream()
                                        .flatMap(t -> t.getDefaultPlayerIds().stream())
                                        .toList();

                                return playerRepository.findByPlayerIdIn(templatePlayerIds).collectList()
                                        .flatMap(templatePlayers -> {
                                            Map<String, Player> templatePlayerMap = templatePlayers.stream()
                                                    .collect(Collectors.toMap(Player::getPlayerId, p -> p));

                                            List<Player> newPlayers = template.getTeams().parallelStream()
                                                    .flatMap(templateTeam -> {
                                                        String teamId = templateTeam.getTeamId();
                                                        return IntStream.range(0, templateTeam.getDefaultPlayerIds().size())
                                                                .mapToObj(i -> {
                                                                    String originalPlayerId = templateTeam.getDefaultPlayerIds().get(i);
                                                                    Player templatePlayer = templatePlayerMap.get(originalPlayerId);
                                                                    if (templatePlayer == null) return null;

                                                                    String playerNumber = String.format("%03d", i + 1);
                                                                    String newPlayerId = newInstanceId + "_" + teamId + "_player_" + playerNumber;

                                                                    Player.Stats baseStats = cloneStats(templatePlayer.getBaseStats());

                                                                    Player newPlayer = new Player();
                                                                    newPlayer.setPlayerId(newPlayerId);
                                                                    newPlayer.set_id(newPlayerId);
                                                                    newPlayer.setTeamId(teamId);
                                                                    newPlayer.setInstanceId(newInstanceId);
                                                                    newPlayer.setLeagueId(baseLeagueId);
                                                                    newPlayer.setCountryId(template.getCountryId());
                                                                    newPlayer.setNationality(templatePlayer.getNationality());
                                                                    newPlayer.setName(templatePlayer.getName());
                                                                    newPlayer.setAge(templatePlayer.getAge());
                                                                    newPlayer.setPosition(templatePlayer.getPosition());
                                                                    newPlayer.setStatus("available");
                                                                    newPlayer.setBaseStats(baseStats);
                                                                    newPlayer.setCurrentStats(cloneStats(baseStats));

                                                                    return newPlayer;
                                                                });
                                                    })
                                                    .filter(Objects::nonNull)
                                                    .collect(Collectors.toList());

                                            Map<String, List<String>> teamToPlayerIds = newPlayers.stream()
                                                    .collect(Collectors.groupingBy(
                                                            Player::getTeamId,
                                                            Collectors.mapping(Player::getPlayerId, Collectors.toList())
                                                    ));

                                            List<LeagueInstance.LeagueInstanceTeam> newTeams = template.getTeams().parallelStream()
                                                    .map(templateTeam -> {
                                                        LeagueInstance.LeagueInstanceTeam team = new LeagueInstance.LeagueInstanceTeam();
                                                        team.setTeamId(templateTeam.getTeamId());
                                                        team.setInstanceId(newInstanceId);
                                                        team.setLeagueId(baseLeagueId);
                                                        team.setCurrentPlayerIds(teamToPlayerIds.getOrDefault(templateTeam.getTeamId(), List.of()));
                                                        team.setCurrentSquadValue(templateTeam.getInitialSquadValue());
                                                        team.setAvailable(true);
                                                        team.setManagerId("");
                                                        team.setManagerName("");
                                                        team.setTeamName(templateTeam.getTeamName());
                                                        return team;
                                                    })
                                                    .collect(Collectors.toList());

                                            LeagueInstance newInstance = new LeagueInstance();
                                            newInstance.set_id(newInstanceId);
                                            newInstance.setInstanceId(newInstanceId);
                                            newInstance.setLeagueId(baseLeagueId);
                                            newInstance.setTemplateId(templateId);
                                            newInstance.setRegionId(template.getRegionId());
                                            newInstance.setCountryId(template.getCountryId());
                                            newInstance.setSeason("2025");
                                            newInstance.setAvailable(true);
                                            newInstance.setCurrentMatchday(0);
                                            newInstance.setReservedTeams(0);
                                            newInstance.setStatus("inactive");
                                            newInstance.setReservedTeamsList(new ArrayList<>());
                                            newInstance.setTeams(newTeams);
                                            newInstance.setStandings(new ArrayList<>());
                                            newInstance.setTransfers(new ArrayList<>());

                                            return playerRepository.saveAll(newPlayers)
                                                    .collectList()
                                                    .timeout(Duration.ofSeconds(90))
                                                    .flatMap(saved -> leagueInstanceRepository.save(newInstance));
                                        });
                            });
                });
    }

    private Player.Stats cloneStats(Player.Stats stats) {
        Player.Stats clone = new Player.Stats();
        clone.setMarketValue(stats.getMarketValue());
        clone.setAttackValue(stats.getAttackValue());
        clone.setDefenseValue(stats.getDefenseValue());
        clone.setOverallValue(stats.getOverallValue());
        clone.setMoralLevel(stats.getMoralLevel());
        clone.setFitnessLevel(stats.getFitnessLevel());
        clone.setExpectedBoost(stats.getExpectedBoost());
        clone.setPendingBoost(stats.getPendingBoost());
        clone.setLastTrainedAt(stats.getLastTrainedAt());
        return clone;
    }

}
