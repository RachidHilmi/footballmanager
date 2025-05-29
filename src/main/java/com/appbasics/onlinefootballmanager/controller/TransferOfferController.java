package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.TransferOffer;
import com.appbasics.onlinefootballmanager.repository.mongo.TransferOfferRepository;
import com.appbasics.onlinefootballmanager.service.TransferOfferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/transfer-offer")
public class TransferOfferController {

    @Autowired
    private TransferOfferService transferOfferService;

    @Autowired
    private TransferOfferRepository transferOfferRepository;

    @PostMapping("/send")
    public Mono<Void> sendOffer(
            @RequestParam String managerId,
            @RequestParam String slotId,
            @RequestParam String fromTeamId,
            @RequestParam String toTeamId,
            @RequestParam String playerId,
            @RequestParam double offerPrice,
            @RequestParam String regionId,
            @RequestParam String instanceId,
            @RequestParam String fromInstanceId
    ) {
        return transferOfferService.createTransferOffer(managerId, slotId, fromTeamId, toTeamId, playerId, offerPrice, regionId, instanceId, fromInstanceId);
    }

    @PostMapping("/evaluate")
    public Mono<Void> evaluateOffers(
            @RequestParam String regionId,
            @RequestParam String instanceId
    ) {
        return transferOfferService.evaluateAIResponses(regionId, instanceId);
    }

    @PostMapping("/reject")
    public Mono<Void> rejectOffers(
            @RequestParam String offerId
    ) {
        return transferOfferService.rejectOffer(offerId);
    }

    @GetMapping("/incoming")
    public Flux<TransferOffer> getIncomingOffers(@RequestParam String toTeamId) {
        return transferOfferRepository.findByToTeamIdAndAcceptedFalseAndRejectedFalse(toTeamId);
    }

    @PostMapping("/accept")
    public Mono<Void> acceptOffer(
            @RequestParam String offerId,
            @RequestParam String managerId,
            @RequestParam String slotId) {
        return transferOfferService.acceptOffer(offerId, managerId, slotId);
    }

    @PostMapping("/counter")
    public Mono<Void> counterOffer(
            @RequestParam String offerId,
            @RequestParam double newPrice,
            @RequestParam String managerId,
            @RequestParam String slotId
    ) {
        return transferOfferService.counterOffer(offerId, newPrice, managerId, slotId);
    }
}
