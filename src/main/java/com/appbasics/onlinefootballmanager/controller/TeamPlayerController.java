package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.Player;
import com.appbasics.onlinefootballmanager.service.TeamPlayerCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/team-players")
public class TeamPlayerController {

    @Autowired
    private TeamPlayerCacheService teamPlayerCacheService;

    @GetMapping
    public Mono<List<Player>> getTeamPlayers(
            @RequestParam String regionId,
            @RequestParam String instanceId,
            @RequestParam String teamId
    ) {
        return teamPlayerCacheService.getTeamPlayers(regionId, instanceId, teamId);
    }

    @GetMapping("/by-ids")
    public Mono<List<Player>> getPlayersByIds(@RequestParam List<String> playerIds) {
        System.out.println("🛠️ Received playerIds: " + playerIds);
        return teamPlayerCacheService.getPlayersByIds(playerIds);
    }
}
