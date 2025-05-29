package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.dto.AvailableTeamDTO;
import com.appbasics.onlinefootballmanager.dto.TeamSelectionRequest;
import com.appbasics.onlinefootballmanager.model.LeagueInstance;
import com.appbasics.onlinefootballmanager.model.LeagueTemplate;
import com.appbasics.onlinefootballmanager.model.Match;
import com.appbasics.onlinefootballmanager.model.TeamDetails;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueTemplateRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamSelectionService {

    private final LeagueInstanceRepository leagueInstanceRepository;
    private final LeagueTemplateRepository leagueTemplateRepository;
    private final LeagueService leagueService;
    private final MatchRepository matchRepository;


//    public Mono<LeagueInstance> assignTeamToManager(TeamSelectionRequest request) {
//        return leagueInstanceRepository.findFirstByInstanceId(request.getInstanceId())
//                .switchIfEmpty(Mono.error(new IllegalStateException("League instance not found")))
//                .flatMap(instance -> {
//                    LeagueInstance.LeagueInstanceTeam targetTeam = instance.getTeams().stream()
//                            .filter(team -> team.getTeamId().equals(request.getTeamId()))
//                            .findFirst()
//                            .orElseThrow(() -> new IllegalStateException("Team not found in league instance"));
//
//                    if (!targetTeam.isAvailable()) {
//                        return Mono.error(new IllegalStateException("Team is already taken"));
//                    }
//
//                    boolean alreadyJoined = instance.getReservedTeamsList().stream()
//                            .anyMatch(rt -> rt.getManagerId().equals(request.getManagerId()));
//                    if (alreadyJoined) {
//                        return Mono.error(new IllegalStateException("Manager already joined this league"));
//                    }
//
//                    targetTeam.setManagerId(request.getManagerId());
//                    targetTeam.setManagerName(request.getManagerName());
//                    targetTeam.setAvailable(false);
//
//                    instance.setReservedTeams(instance.getReservedTeams() + 1);
//                    instance.getReservedTeamsList().add(new LeagueInstance.ReservedTeam(
//                            request.getTeamId(), request.getManagerId()
//                    ));
//
//                    boolean isFirstTeam = (instance.getReservedTeams() == 1);
//                    if (isFirstTeam) {
//                        instance.setStatus("preparation");
//                        instance.setCurrentMatchday(1);
//                        instance.setPreparationStart(new Date());
//                        instance.setStatus("active");
//                    }
//
//                    if (instance.getReservedTeams() == instance.getTeams().size()) {
//                        instance.setStatus("active");
//                    }
//
//                    return leagueInstanceRepository.save(instance)
//                            .flatMap(savedInstance -> {
//                                if (isFirstTeam) {
//                                    return leagueService.initializeStandingsIfEmpty(request.getInstanceId())
//                                            .then(generateFixtures(savedInstance.getInstanceId()))
//                                            .thenReturn(savedInstance);
//                                } else {
//                                    return Mono.just(savedInstance);
//                                }
//                            });
//                });
//    }

    public Mono<LeagueInstance> assignTeamToManager(TeamSelectionRequest request) {
        return leagueInstanceRepository.findFirstByInstanceId(request.getInstanceId())
                .switchIfEmpty(Mono.error(new IllegalStateException("League instance not found")))
                .flatMap(instance -> {
                    LeagueInstance.LeagueInstanceTeam targetTeam = instance.getTeams().stream()
                            .filter(team -> team.getTeamId().equals(request.getTeamId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Team not found in league instance"));

                    if (!targetTeam.isAvailable()) {
                        return Mono.error(new IllegalStateException("Team is already taken"));
                    }

                    boolean alreadyJoined = instance.getReservedTeamsList().stream()
                            .anyMatch(rt -> rt.getManagerId().equals(request.getManagerId()));
                    if (alreadyJoined) {
                        return Mono.error(new IllegalStateException("Manager already joined this league"));
                    }

                    targetTeam.setManagerId(request.getManagerId());
                    targetTeam.setManagerName(request.getManagerName());
                    targetTeam.setAvailable(false);

                    instance.setReservedTeams(instance.getReservedTeams() + 1);
                    instance.getReservedTeamsList().add(new LeagueInstance.ReservedTeam(
                            request.getTeamId(), request.getManagerId()
                    ));

                    boolean isFirstTeam = (instance.getReservedTeams() == 1);
                    if (isFirstTeam) {
                        instance.setStatus("preparation");
                        instance.setCurrentMatchday(1);
                        instance.setPreparationStart(new Date());
                        instance.setStatus("active");
                    }

                    if (instance.getReservedTeams() == instance.getTeams().size()) {
                        instance.setStatus("active");
                    }

                    return leagueInstanceRepository.save(instance)
                            .doOnSuccess(savedInstance -> {
                                if (isFirstTeam) {
                                    // Run async tasks in background thread
                                    asyncInitLeague(savedInstance.getInstanceId()).subscribe();
                                }
                            });
                });
    }

    public Mono<Void> asyncInitLeague(String instanceId) {
        return leagueService.initializeStandingsIfEmpty(instanceId)
                .then(generateFixtures(instanceId))
                .subscribeOn(Schedulers.boundedElastic()) // Run in separate thread
                .onErrorResume(e -> {
                    System.err.println("Async init failed for instance: " + instanceId);
                    return Mono.empty(); // Avoid crash
                });
    }


    public Mono<Void> generateFixtures(String instanceId) {
        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                .flatMap(instance -> {
                    List<LeagueInstance.LeagueInstanceTeam> teams = instance.getTeams();
                    if (teams == null || teams.size() < 2) {
                        return Mono.error(new IllegalStateException("Not enough teams to generate fixtures"));
                    }

                    List<LeagueInstance.LeagueInstanceTeam> sortedTeams = new ArrayList<>(teams);
                    sortedTeams.sort(Comparator.comparing(LeagueInstance.LeagueInstanceTeam::getTeamId));

                    boolean hasOdd = sortedTeams.size() % 2 != 0;
                    if (hasOdd) {
                        sortedTeams.add(null); // Add bye
                    }

                    int teamCount = sortedTeams.size();
                    int rounds = teamCount - 1;

                    List<List<Pair<LeagueInstance.LeagueInstanceTeam, LeagueInstance.LeagueInstanceTeam>>> schedule = new ArrayList<>();

                    // First leg (home/away alternated per round)
                    for (int round = 0; round < rounds; round++) {
                        List<Pair<LeagueInstance.LeagueInstanceTeam, LeagueInstance.LeagueInstanceTeam>> matchday = new ArrayList<>();
                        for (int i = 0; i < teamCount / 2; i++) {
                            LeagueInstance.LeagueInstanceTeam t1 = sortedTeams.get(i);
                            LeagueInstance.LeagueInstanceTeam t2 = sortedTeams.get(teamCount - 1 - i);
                            if (t1 != null && t2 != null) {
                                // Alternate home/away by round
                                if ((round + i) % 2 == 0) {
                                    matchday.add(Pair.of(t1, t2));
                                } else {
                                    matchday.add(Pair.of(t2, t1));
                                }
                            }
                        }
                        schedule.add(matchday);

                        // Rotate teams for next round (keeping first team in place)
                        List<LeagueInstance.LeagueInstanceTeam> rotated = new ArrayList<>(sortedTeams);
                        LeagueInstance.LeagueInstanceTeam fixed = rotated.remove(0);
                        LeagueInstance.LeagueInstanceTeam last = rotated.remove(rotated.size() - 1);
                        rotated.add(0, last);
                        rotated.add(0, fixed);
                        sortedTeams = rotated;
                    }

                    // Second leg — reverse home/away
                    List<List<Pair<LeagueInstance.LeagueInstanceTeam, LeagueInstance.LeagueInstanceTeam>>> secondLeg = schedule.stream()
                            .map(round -> round.stream()
                                    .map(pair -> Pair.of(pair.getRight(), pair.getLeft()))
                                    .collect(Collectors.toList()))
                            .collect(Collectors.toList());

                    List<List<Pair<LeagueInstance.LeagueInstanceTeam, LeagueInstance.LeagueInstanceTeam>>> fullSchedule = new ArrayList<>();
                    for (int i = 0; i < schedule.size(); i++) {
                        fullSchedule.add(schedule.get(i));
                        fullSchedule.add(secondLeg.get(i));
                    }

                    // Convert to Match objects
                    List<Match> matches = new ArrayList<>();
                    ZoneId zone = ZoneId.of("UTC");
                    LocalDateTime baseDate = LocalDate.now(zone).atTime(20, 0);
                    int matchId = 1;

                    for (int matchday = 0; matchday < fullSchedule.size(); matchday++) {
                        Date matchDate = Date.from(baseDate.plusDays(matchday).atZone(zone).toInstant());

                        for (Pair<LeagueInstance.LeagueInstanceTeam, LeagueInstance.LeagueInstanceTeam> pair : fullSchedule.get(matchday)) {
                            LeagueInstance.LeagueInstanceTeam home = pair.getLeft();
                            LeagueInstance.LeagueInstanceTeam away = pair.getRight();

                            Match match = new Match();
                            match.set_id(instanceId + "_match_" + matchId);
                            match.setMatchId(instanceId + "_match_" + matchId);
                            match.setInstanceId(instanceId);
                            match.setLeagueId(instance.getLeagueId());
                            match.setMatchday(matchday + 1);
                            match.setSeason(instance.getSeason());
                            match.setStatus("pending");
                            match.setMatchDate(matchDate);
                            match.setTeamA(new TeamDetails(home.getManagerId(), home.getManagerName(), home.getTeamId(), home.getTeamName()));
                            match.setTeamB(new TeamDetails(away.getManagerId(), away.getManagerName(), away.getTeamId(), away.getTeamName()));

                            matches.add(match);
                            matchId++;
                        }
                    }

                    return matchRepository.saveAll(matches).then();
                });
    }

    public Flux<AvailableTeamDTO> getAvailableTeams(String instanceId) {
        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Instance not found")))
                .flatMapMany(instance -> leagueTemplateRepository.findById(instance.getTemplateId())
                        .flatMapMany(template -> {
                            Map<String, LeagueTemplate.LeagueTemplateTeam> templateMap = template.getTeams().stream()
                                    .collect(Collectors.toMap(LeagueTemplate.LeagueTemplateTeam::getTeamId, Function.identity()));

                            List<AvailableTeamDTO> result = instance.getTeams().stream()
                                    .filter(LeagueInstance.LeagueInstanceTeam::isAvailable)
                                    .map(team -> {
                                        LeagueTemplate.LeagueTemplateTeam templateTeam = templateMap.get(team.getTeamId());
                                        return new AvailableTeamDTO(
                                                team.getTeamId(),
                                                templateTeam != null ? templateTeam.getTeamName() : team.getTeamId(),
                                                templateTeam != null ? templateTeam.getTeamObjective() : "",
                                                team.getCurrentSquadValue(),
                                                team.getLeagueId(),
                                                instance.getCountryId(),
                                                instance.getTeams().size(),
                                                instance.getReservedTeams()
                                        );
                                    }).toList();

                            return Flux.fromIterable(result);
                        }));
    }



}

