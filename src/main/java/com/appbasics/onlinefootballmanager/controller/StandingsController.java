package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.Standing;
import com.appbasics.onlinefootballmanager.repository.mongo.StandingsRepository;
import com.appbasics.onlinefootballmanager.service.LeagueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/standings")
public class StandingsController {

    @Autowired
    private StandingsRepository standingsRepository;

    @GetMapping("/{instanceId}")
    public Flux<Standing> getSmartOrderedStandings(@PathVariable String instanceId) {
        return standingsRepository.findByInstanceId(instanceId)
                .collectList()
                .flatMapMany(standings -> {
                    if (standings.stream().allMatch(s -> s.getPoints() == 0 && s.getMatchesPlayed() == 0)) {
                        // No matches played yet → alphabetical order
                        standings.sort(Comparator.comparing(Standing::getTeamId, String.CASE_INSENSITIVE_ORDER));
                    } else {
                        // Matches have been played → ranking order
                        standings.sort(Comparator.comparing(Standing::getPoints, Comparator.reverseOrder())
                                .thenComparing(Standing::getGoalDifference, Comparator.reverseOrder())
                                .thenComparing(Standing::getGoalsFor, Comparator.reverseOrder()));
                    }
                    return Flux.fromIterable(standings);
                });
    }
}
