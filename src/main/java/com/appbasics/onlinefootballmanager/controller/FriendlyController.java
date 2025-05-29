package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.dto.FriendlyRequest;
import com.appbasics.onlinefootballmanager.dto.TeamListItem;
import com.appbasics.onlinefootballmanager.model.MatchResult;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.service.FriendlyService;
import com.appbasics.onlinefootballmanager.service.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/friendlies")
public class FriendlyController {

    @Autowired
    private LeagueInstanceRepository leagueInstanceRepository;
    @Autowired
    private MatchService matchService;
    @Autowired
    private FriendlyService friendlyService;

    @GetMapping("/{instanceId}/teams/{teamId}")
    public Mono<List<TeamListItem>> getAllTeamsWithNextOpponent(
            @PathVariable String instanceId,
            @PathVariable String teamId) {

        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                .flatMap(instance -> {
                    int currentMatchday = instance.getCurrentMatchday();

                    // Get the manager ID of the requesting team
                    String managerId = instance.getTeams().stream()
                            .filter(t -> t.getTeamId().equals(teamId))
                            .map(t -> t.getManagerId())
                            .findFirst()
                            .orElse(null);

                    if (managerId == null) return Mono.error(new RuntimeException("Manager not found"));

                    return matchService.getMatchesForTeamOnMatchday(instanceId, teamId, currentMatchday)
                            .collectList()
                            .flatMap(matches -> {
                                String nextOpponentId = matches.stream()
                                        .map(m -> m.getTeamA().getTeamId().equals(teamId)
                                                ? m.getTeamB().getTeamId()
                                                : m.getTeamA().getTeamId())
                                        .findFirst().orElse(null);

                                return friendlyService.getFriendliesPlayedToday(instanceId, managerId)
                                        .collectMap(fm -> fm.getTargetTeamId())
                                        .map(friendlyMap -> {
                                            return instance.getTeams().stream()
                                                    .filter(t -> !t.getTeamId().equals(teamId)) // exclude own team
                                                    .map(t -> {
                                                        boolean isOpponent = t.getTeamId().equals(nextOpponentId);
                                                        boolean playedToday = friendlyMap.containsKey(t.getTeamId());

                                                        String result = null;
                                                        if (playedToday) {
                                                            var fm = friendlyMap.get(t.getTeamId());
                                                            int a = fm.getResult().getTeamA_score();
                                                            int b = fm.getResult().getTeamB_score();
                                                            boolean win = a > b;
                                                            boolean draw = a == b;
                                                            result = (win ? "W " : draw ? "D " : "L ") + a + "–" + b;
                                                        }

                                                        return new TeamListItem(
                                                                t.getTeamId(),
                                                                t.getTeamName(),
                                                                t.getManagerId() != null ? t.getManagerName() : "Computer",
                                                                t.getManagerId(),
                                                                isOpponent,
                                                                playedToday,
                                                                result
                                                        );
                                                    })
                                                    .collect(Collectors.toList());
                                        });
                            });
                });
    }


    @PostMapping("/play")
    public Mono<ResponseEntity<MatchResult>> playFriendly(@RequestBody FriendlyRequest request) {
        return leagueInstanceRepository.findFirstByInstanceId(request.getInstanceId())
                .flatMap(instance -> {
                    String initiatorManagerId = instance.getTeams().stream()
                            .filter(t -> t.getTeamId().equals(request.getInitiatorTeamId()))
                            .map(t -> t.getManagerId())
                            .findFirst()
                            .orElse(null);

                    if (initiatorManagerId == null || initiatorManagerId.isEmpty()) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }

                    return friendlyService.simulateFriendlyMatch(
                                    request.getInstanceId(),
                                    initiatorManagerId,
                                    request.getTargetTeamId()
                            ).map(ResponseEntity::ok)
                            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(null)));
                });
    }
}

