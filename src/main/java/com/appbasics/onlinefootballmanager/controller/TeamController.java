package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.LeagueInstance;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {

    private final LeagueInstanceRepository leagueInstanceRepo;

    @GetMapping("/{leagueInstanceId}/available-teams")
    public Flux<LeagueInstance.LeagueInstanceTeam> getAvailableTeams(@PathVariable String leagueInstanceId) {
        return leagueInstanceRepo.findById(leagueInstanceId)
                .flatMapMany(instance -> Flux.fromStream(
                        instance.getTeams().stream().filter(LeagueInstance.LeagueInstanceTeam::isAvailable)));
    }
}
