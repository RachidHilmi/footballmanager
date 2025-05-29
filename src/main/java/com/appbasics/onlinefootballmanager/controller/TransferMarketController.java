package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.TransferListing;
import com.appbasics.onlinefootballmanager.service.TransferMarketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/transfer-market")
public class TransferMarketController {

    @Autowired
    private TransferMarketService transferMarketService;

    @PostMapping("/list")
    public Mono<Void> listPlayer(
            @RequestParam String regionId,
            @RequestParam String instanceId,
            @RequestParam String teamId,
            @RequestParam String managerId,
            @RequestParam String slotId,
            @RequestParam String playerId,
            @RequestParam int playerAge,
            @RequestParam double askingPrice,
            @RequestParam double playerMarketValue
    ) {
        return transferMarketService.listPlayerForTransfer(
                regionId, instanceId, teamId, managerId, slotId, playerId, playerAge, askingPrice, playerMarketValue
        );
    }

    @DeleteMapping("/cancel-listing")
    public Mono<Void> cancelListing(
            @RequestParam String regionId,
            @RequestParam String instanceId,
            @RequestParam String managerId,
            @RequestParam String slotId,
            @RequestParam String listingId,
            @RequestParam String teamId
    ) {
        return transferMarketService.cancelTransferListing(regionId, instanceId, managerId, slotId, listingId, teamId);
    }

    @GetMapping("/available")
    public Mono<List<TransferListing>> getAvailableListings(
            @RequestParam String regionId,
            @RequestParam String instanceId
    ) {
        return transferMarketService.getAvailableListings(regionId, instanceId);
    }

    @PostMapping("/buy")
    public Mono<Void> buyPlayer(
            @RequestParam String regionId,
            @RequestParam String instanceId,
            @RequestParam String managerId,
            @RequestParam String slotId,
            @RequestParam String listingId
    ) {
        return transferMarketService.buyPlayerFromMarket(regionId, instanceId, managerId, slotId, listingId);
    }

    @GetMapping("/test-generate")
    public Mono<String> testGenerateSystemListings(@RequestParam String regionId,
                                                   @RequestParam String instanceId) {
        return transferMarketService.generateSystemListings(regionId, instanceId, 40)
                .thenReturn("✅ Test listing generation completed");
    }
}
