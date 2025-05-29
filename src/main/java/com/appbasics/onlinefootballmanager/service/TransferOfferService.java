package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.*;
import com.appbasics.onlinefootballmanager.repository.firestore.ManagerRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.PlayerRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.TransferOfferRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.*;


@Slf4j
@Service
public class TransferOfferService {

    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TransferOfferRepository transferOfferRepository;
    @Autowired
    private ManagerRepository managerRepository;
    @Autowired
    private LeagueInstanceRepository leagueInstanceRepository;
    @Autowired
    private TransferHistoryService transferHistoryService;

    public Mono<Void> createTransferOffer(String fromManagerId, String slotId, String fromTeamId, String toTeamId,
                                          String playerId, double offerPrice, String regionId,
                                          String instanceId, String fromLeagueId) {
        return managerRepository.findByManagerId(fromManagerId)
                .flatMap(manager -> {
                    if (!manager.getSlots().containsKey(slotId)) {
                        return Mono.error(new IllegalArgumentException("Slot not found"));
                    }

                    String ownedTeamId = manager.getSlots().get(slotId).getTeamId();
                    if (ownedTeamId.equals(toTeamId)) {
                        return Mono.error(new IllegalArgumentException("Cannot send offer to your own team."));
                    }

                    double budget = Double.parseDouble(manager.getSlots().get(slotId).getBudget());
                    if (budget < offerPrice) {
                        return Mono.error(new IllegalStateException("Insufficient budget to send offer"));
                    }

                    TransferOffer offer = new TransferOffer(
                            UUID.randomUUID().toString(),
                            fromTeamId.trim(),
                            toTeamId.trim(),
                            playerId,
                            regionId,
                            instanceId,
                            fromLeagueId,
                            offerPrice,
                            System.currentTimeMillis(),
                            false,
                            false,
                            null
                    );

                    return transferOfferRepository.save(offer).then();
                });
    }

    public Mono<Void> evaluateAIResponses(String regionId, String instanceId) {
        return transferOfferRepository.findByRegionIdAndInstanceIdAndAcceptedFalseAndRejectedFalse(regionId, instanceId)
                .flatMap(offer ->
                        playerRepository.findByInstanceIdAndTeamId(instanceId, offer.getToTeamId())
                                .filter(player -> player.getPlayerId().equals(offer.getPlayerId()))
                                .next()
                                .flatMap(player -> {
                                    double marketValue = TransferMarketService.parseMarketValue(player.getCurrentStats().getMarketValue());
                                    double offerValue = offer.getOfferPrice();
                                    double delta = offerValue - marketValue;

                                    if (delta >= 0) {
                                        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                                                .flatMap(instance -> {
                                                    LeagueInstance.LeagueInstanceTeam sellerTeam = instance.getTeams().stream()
                                                            .filter(t -> t.getTeamId().equals(offer.getToTeamId()))
                                                            .findFirst().orElse(null);
                                                    LeagueInstance.LeagueInstanceTeam buyerTeam = instance.getTeams().stream()
                                                            .filter(t -> t.getTeamId().equals(offer.getFromTeamId()))
                                                            .findFirst().orElse(null);

                                                    if (buyerTeam == null || sellerTeam == null || buyerTeam.getManagerId() == null) {
                                                        offer.setRejected(true);
                                                        return transferOfferRepository.save(offer).then();
                                                    }

                                                    return managerRepository.findByManagerId(buyerTeam.getManagerId())
                                                            .flatMap(manager -> {
                                                                Slot slot = manager.getSlots().values().stream()
                                                                        .filter(s -> s.getTeamId().equals(buyerTeam.getTeamId()))
                                                                        .findFirst().orElse(null);
                                                                if (slot == null) return Mono.error(new IllegalStateException("Buyer slot not found"));

                                                                double budget = TransferMarketService.parseMarketValue(slot.getBudget());
                                                                if (budget < offerValue) {
                                                                    offer.setRejected(true);
                                                                    return transferOfferRepository.save(offer).then();
                                                                }

                                                                offer.setAccepted(true);
                                                                double remaining = budget - offerValue;
                                                                slot.setBudget(String.format("%.2f", remaining));

                                                                return generateUniquePlayerId(offer.getFromInstanceId(), offer.getFromTeamId())
                                                                        .flatMap(newId -> {
                                                                            String oldTeamId = player.getTeamId();
                                                                            String oldLeagueId = player.getLeagueId();

                                                                            if (player.getOriginalTeamId() == null || player.getOriginalTeamId().isEmpty()) {
                                                                                player.setOriginalTeamId(oldTeamId); // store seller team as the original
                                                                            }

                                                                            player.setPlayerId(newId);
                                                                            player.setTeamId(offer.getFromTeamId());
                                                                            player.setLeagueId(offer.getFromInstanceId());
                                                                            player.setInstanceId(offer.getFromInstanceId());

                                                                            TransferRecord record = new TransferRecord(
                                                                                    UUID.randomUUID().toString(),
                                                                                    newId,
                                                                                    sellerTeam.getManagerId(),
                                                                                    buyerTeam.getManagerId(),
                                                                                    oldTeamId,
                                                                                    offer.getFromTeamId(),
                                                                                    offer.getFromInstanceId(),
                                                                                    offer.getFromInstanceId(),
                                                                                    oldLeagueId,
                                                                                    regionId,
                                                                                    offerValue,
                                                                                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()),
                                                                                    false
                                                                            );

                                                                            return playerRepository.save(player)
                                                                                    .then(transferOfferRepository.save(offer))
                                                                                    .then(managerRepository.save(manager))
                                                                                    .then(transferHistoryService.saveTransferRecord(record))
                                                                                    .then(saveToLeagueTransfers(instanceId, record));
                                                                        });
                                                            });
                                                });
                                    } else if (delta >= -marketValue * 0.2) {
                                        offer.setCounterOfferPrice(marketValue * 1.05);
                                        return transferOfferRepository.save(offer).then();
                                    } else {
                                        offer.setRejected(true);
                                        return transferOfferRepository.save(offer).then();
                                    }
                                })
                ).then();
    }

    private double parseBudget(String budgetStr) {
        try {
            return Double.parseDouble(Optional.ofNullable(budgetStr).orElse("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseMarketValue(String valueStr) {
        try {
            return Double.parseDouble(valueStr.replaceAll("[^\\d.]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public Mono<Void> counterOffer(String offerId, double newPrice, String managerId, String slotId) {
        return managerRepository.findByManagerId(managerId)
                .flatMap(manager -> {
                    Slot slot = manager.getSlots().get(slotId);
                    if (slot == null) {
                        return Mono.error(new IllegalArgumentException("Slot not found"));
                    }

                    double budget;
                    try {
                        budget = Double.parseDouble(Optional.ofNullable(slot.getBudget()).orElse("0"));
                    } catch (NumberFormatException e) {
                        return Mono.error(new IllegalStateException("Invalid budget format"));
                    }

                    if (budget < newPrice) {
                        return Mono.error(new IllegalStateException("Insufficient budget to send counter offer"));
                    }

                    return transferOfferRepository.findById(offerId)
                            .flatMap(offer -> {
                                offer.setCounterOfferPrice(newPrice);
                                return transferOfferRepository.save(offer)
                                        .doOnSuccess(saved -> log.info("Updated counterOfferPrice to: {}", saved.getCounterOfferPrice()));
                            });
                }).then();
    }

    public Mono<Void> rejectOffer(String offerId) {
        return transferOfferRepository.findById(offerId)
                .flatMap(offer -> {
                    offer.setRejected(true);
                    return transferOfferRepository.save(offer);
                })
                .then();
    }

    public Mono<Void> acceptOffer(String offerId, String buyerManagerId, String buyerSlotId) {
        return managerRepository.findByManagerId(buyerManagerId)
                .flatMap(buyerManager -> {
                    Slot buyerSlot = buyerManager.getSlots().get(buyerSlotId);
                    if (buyerSlot == null) {
                        return Mono.error(new IllegalArgumentException("Buyer slot not found"));
                    }

                    double buyerBudget = TransferMarketService.parseMarketValue(buyerSlot.getBudget());

                    return transferOfferRepository.findById(offerId)
                            .flatMap(offer -> {
                                double price = offer.getCounterOfferPrice() != null
                                        ? offer.getCounterOfferPrice()
                                        : offer.getOfferPrice();

                                if (buyerBudget < price) {
                                    return Mono.error(new IllegalStateException("Insufficient budget to accept offer"));
                                }

                                return leagueInstanceRepository.findFirstByInstanceId(offer.getInstanceId())
                                        .flatMap(instance -> {
                                            LeagueInstance.LeagueInstanceTeam sellerTeam = instance.getTeams().stream()
                                                    .filter(t -> t.getTeamId().equals(offer.getToTeamId()))
                                                    .findFirst().orElse(null);

                                            String sellerManagerId = sellerTeam != null ? sellerTeam.getManagerId() : null;

                                            Mono<Void> updateSellerBudget;
                                            if (sellerManagerId != null && !sellerManagerId.equals("AI")) {
                                                updateSellerBudget = managerRepository.findByManagerId(sellerManagerId)
                                                        .flatMap(sellerManager -> {
                                                            Slot sellerSlot = sellerManager.getSlots().values().stream()
                                                                    .filter(s -> offer.getToTeamId().equals(s.getTeamId()))
                                                                    .findFirst().orElse(null);
                                                            if (sellerSlot == null) return Mono.empty();

                                                            double current = TransferMarketService.parseMarketValue(sellerSlot.getBudget());
                                                            sellerSlot.setBudget(String.format("%.2f", current + price));
                                                            return managerRepository.save(sellerManager).then();
                                                        });
                                            } else {
                                                updateSellerBudget = Mono.empty();
                                            }

                                            return playerRepository.findByInstanceIdAndTeamId(offer.getInstanceId(), offer.getToTeamId())
                                                    .filter(p -> p.getPlayerId().equals(offer.getPlayerId()))
                                                    .next()
                                                    .flatMap(player -> generateUniquePlayerId(offer.getFromInstanceId(), offer.getFromTeamId())
                                                            .flatMap(newId -> {
                                                                String oldTeamId = player.getTeamId();
                                                                String oldLeagueId = player.getLeagueId();

                                                                if (player.getOriginalTeamId() == null || player.getOriginalTeamId().isEmpty()) {
                                                                    player.setOriginalTeamId(oldTeamId); // store seller team as the original
                                                                }

                                                                player.setPlayerId(newId);
                                                                player.setTeamId(offer.getFromTeamId());
                                                                player.setLeagueId(offer.getFromInstanceId());
                                                                player.setInstanceId(offer.getFromInstanceId());

                                                                offer.setAccepted(true);
                                                                double remaining = buyerBudget - price;
                                                                buyerSlot.setBudget(String.format("%.2f", remaining));

                                                                TransferRecord record = new TransferRecord(
                                                                        UUID.randomUUID().toString(),
                                                                        newId,
                                                                        sellerManagerId,
                                                                        buyerManagerId,
                                                                        oldTeamId,
                                                                        offer.getFromTeamId(),
                                                                        offer.getFromInstanceId(),
                                                                        offer.getFromInstanceId(),
                                                                        oldLeagueId,
                                                                        offer.getRegionId(),
                                                                        price,
                                                                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()),
                                                                        false
                                                                );

                                                                return playerRepository.save(player)
                                                                        .then(transferOfferRepository.save(offer))
                                                                        .then(managerRepository.save(buyerManager))
                                                                        .then(transferHistoryService.saveTransferRecord(record))
                                                                        .then(updateSellerBudget)
                                                                        .then(saveToLeagueTransfers(offer.getFromInstanceId(), record));
                                                            }));
                                        });
                            });
                });
    }

    private Mono<String> generateUniquePlayerId(String instanceId, String teamId) {
        return playerRepository.findByInstanceIdAndTeamId(instanceId, teamId)
                .collectList()
                .map(players -> {
                    int maxSuffix = players.stream()
                            .map(Player::getPlayerId)
                            .map(id -> {
                                String[] parts = id.split("_");
                                try {
                                    return Integer.parseInt(parts[parts.length - 1]);
                                } catch (NumberFormatException e) {
                                    return 0;
                                }
                            }).max(Integer::compareTo).orElse(0);
                    return instanceId + "_" + teamId + "_player_" + (maxSuffix + 1);
                });
    }

    private Mono<Void> saveToLeagueTransfers(String instanceId, TransferRecord record) {
        return leagueInstanceRepository.findFirstByInstanceId(instanceId)
                .flatMap(instance -> {
                    if (instance.getTransfers() == null) {
                        instance.setTransfers(new ArrayList<>());
                    }
                    if (instance.getTransfers().size() >= 100) {
                        instance.getTransfers().remove(0);
                    }
                    instance.getTransfers().add(record);
                    return leagueInstanceRepository.save(instance).then();
                });
    }

}
