package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.CardInstance;
import com.appbasics.onlinefootballmanager.model.TrainingCard;
import com.appbasics.onlinefootballmanager.service.CardService;
import com.appbasics.onlinefootballmanager.service.PlayerService;
import com.appbasics.onlinefootballmanager.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final RewardService rewardService;
    private final PlayerService playerService;

    @GetMapping("/all")
    public Mono<List<TrainingCard>> getAllCardTypes() {
        return cardService.getAllCardTypes();
    }

    @GetMapping("/{managerId}")
    public Mono<List<CardInstance>> getManagerCards(@PathVariable String managerId) {
        return cardService.getManagerCards(managerId);
    }

    @PostMapping("/{managerId}/use/{instanceId}")
    public Mono<Void> useCard(@PathVariable String managerId, @PathVariable String instanceId) {
        return cardService.useCard(managerId, instanceId);
    }

    @PostMapping("/{managerId}/add")
    public Mono<Void> addCard(@PathVariable String managerId, @RequestBody CardInstance instance) {
        return cardService.addCardToManager(managerId, instance);
    }

    @GetMapping("/rewards/check/{managerId}")
    public Mono<List<CardInstance>> checkRewards(@PathVariable String managerId) {
        return rewardService.checkAndGrantRewards(managerId);
    }

    @PostMapping("/apply")
    public Mono<ResponseEntity<Void>> applyCardToPlayer(
            @RequestParam String managerId,
            @RequestParam String playerId,
            @RequestParam String cardInstanceId
    ) {
        return playerService.applyCardToPlayer(managerId, playerId, cardInstanceId)
                .thenReturn(ResponseEntity.ok().build());
    }
}
