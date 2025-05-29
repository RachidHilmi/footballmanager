package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.Player;
import com.appbasics.onlinefootballmanager.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    // Fetch players for a team
    @GetMapping("/{regionId}/{instanceId}/{teamId}")
    public Flux<Player> getPlayersForTeam(@PathVariable String leagueId,
                                          @PathVariable String teamId) {
        return playerService.getPlayersForTeam(leagueId, teamId);
    }

    // Reset players' stats at the end of the season
    @PostMapping("/{regionId}/{instanceId}/{teamId}/reset")
    public Mono<ResponseEntity<String>> resetPlayerStats(@PathVariable String instanceId,
                                                         @PathVariable String teamId) {
        return playerService.resetPlayerStats(instanceId, teamId)
                .thenReturn(ResponseEntity.ok("Player stats reset successfully"));
    }

    @PostMapping("/{regionId}/{instanceId}/{teamId}/startTraining/{playerId}")
    public Mono<ResponseEntity<String>> startTraining(@PathVariable String playerId,
                                                      @RequestParam String managerId,
                                                      @RequestParam(required = false) String cardInstanceId,
                                                      @RequestParam String slotId,
                                                      @RequestParam int slotIndex) {
        return playerService.startTraining(playerId, managerId, cardInstanceId, slotId, slotIndex)
                .thenReturn(ResponseEntity.ok("Training Started"))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().body("Start failed: " + e.getMessage())));
    }

    @PostMapping("/{regionId}/{instanceId}/{teamId}/completeTraining/{playerId}")
    public Mono<ResponseEntity<String>> completeTraining(@PathVariable String playerId,
                                                         @RequestParam String managerId,
                                                         @RequestParam(required = false) String cardInstanceId,
                                                         @RequestParam String slotId,
                                                         @RequestParam int slotIndex) {
        return playerService.completeTraining(playerId, managerId, cardInstanceId, slotId, slotIndex)
                .thenReturn(ResponseEntity.ok("Training Completed and Stats Updated"))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().body("Completion failed: " + e.getMessage())));
    }

    @PostMapping("/{regionId}/{instanceId}/{teamId}/{playerId}/reduceCooldown")
    public Mono<ResponseEntity<String>> reduceCooldown(@PathVariable String playerId,
                                                       @RequestParam String managerId,
                                                       @RequestParam(required = false) String cardInstanceId) {
        return playerService.reduceCooldown(playerId, managerId, cardInstanceId)
                .thenReturn(ResponseEntity.ok("Cooldown reduced successfully."))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().body("Cooldown reduction failed: " + e.getMessage())));
    }

    @PostMapping("/{regionId}/{instanceId}/{teamId}/{playerId}/skipCooldown")
    public Mono<ResponseEntity<String>> skipCooldown( @PathVariable String playerId,
                                                     @RequestParam String managerId,
                                                     @RequestParam(required = false) String cardInstanceId,
                                                     @RequestParam(defaultValue = "0") int coinCost) {
        return playerService.skipCooldown(playerId, managerId, cardInstanceId, coinCost)
                .thenReturn(ResponseEntity.ok("Cooldown skipped successfully."))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().body("Cooldown skip failed: " + e.getMessage())));
    }


}
