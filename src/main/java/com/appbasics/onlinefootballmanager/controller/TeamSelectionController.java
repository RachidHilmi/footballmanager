package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.dto.AvailableTeamDTO;
import com.appbasics.onlinefootballmanager.dto.TeamSelectionRequest;
import com.appbasics.onlinefootballmanager.model.LeagueInstance;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.service.TeamSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


@RestController
@RequestMapping("/api/team-selection")
@RequiredArgsConstructor
public class TeamSelectionController {

    private final TeamSelectionService teamSelectionService;
    private final LeagueInstanceRepository leagueInstanceRepository;

    @PostMapping("/assign-team")
    public Mono<LeagueInstance> assignTeam(@RequestBody TeamSelectionRequest request) {
        return teamSelectionService.assignTeamToManager(request);
    }

    @GetMapping("/teams/{instanceId}")
    public Flux<AvailableTeamDTO> getAvailableTeams(@PathVariable String instanceId) {
        return teamSelectionService.getAvailableTeams(instanceId);
    }

    @GetMapping("/teams-in-league/{instanceId}")
    public Mono<List<LeagueInstance.LeagueInstanceTeam>> getTeamsInLeague(@PathVariable String instanceId) {
        return leagueInstanceRepository.findByInstanceId(instanceId)
                .map(LeagueInstance::getTeams);
    }

}

