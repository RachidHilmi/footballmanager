package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.CardInstance;
import com.appbasics.onlinefootballmanager.model.Player;
import com.appbasics.onlinefootballmanager.model.TrainingCard;
import com.appbasics.onlinefootballmanager.repository.mongo.PlayerRepository;
import com.appbasics.onlinefootballmanager.util.FirestoreUtils;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.database.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class PlayerService {

    @Autowired
    private Firestore firestore;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private RewardService rewardService;
    @Autowired
    private CardService cardService;

    public Flux<Player> getPlayersForTeam(String instanceId, String teamId) {
        return playerRepository.findByInstanceIdAndTeamId(instanceId, teamId);
    }

    public Mono<Void> resetPlayerStats(String instanceId, String teamId) {
        return playerRepository.findByInstanceIdAndTeamId(instanceId, teamId)
                .flatMap(player -> {
                    Player.Stats baseStats = Optional.ofNullable(player.getBaseStats())
                            .orElse(new Player.Stats()); // default empty stats

                    // Clone baseStats → new currentStats
                    Player.Stats newStats = new Player.Stats();

//                    newStats.setMarketValue(baseStats.getMarketValue());
//                    newStats.setAttackValue(baseStats.getAttackValue());
//                    newStats.setDefenseValue(baseStats.getDefenseValue());
//                    newStats.setOverallValue(baseStats.getOverallValue());
//                    newStats.setMoralLevel(baseStats.getMoralLevel());
//                    newStats.setFitnessLevel(baseStats.getFitnessLevel());

                    newStats.setPendingBoost(0);
                    newStats.setExpectedBoost(0);
                    newStats.setLastTrainedAt(0);

                    player.setCurrentStats(newStats);
                    player.setStatus("available");

                    return playerRepository.save(player);
                })
                .then();
    }

    public Mono<Void> startTraining(String playerId, String managerId, @Nullable String cardInstanceId, String slotId, int slotIndex) {
        return playerRepository.findById(playerId)
                .switchIfEmpty(Mono.error(new RuntimeException("Player not found")))
                .flatMap(player -> {
                    Player.Stats current = player.getCurrentStats();
                    long now = System.currentTimeMillis();
                    long lastTrained = toLong(current.getLastTrainedAt());
                    long cooldown = 6 * 60 * 60 * 1000L;

                    if (now - lastTrained < cooldown) {
                        return Mono.error(new RuntimeException("Training is on cooldown."));
                    }

                    int age = player.getAge();
                    final double[] baseBoost = { calculateBaseBoost(age) };

                    Mono<Void> applyCard = Mono.empty();
                    if (cardInstanceId != null && !cardInstanceId.isEmpty()) {
                        DocumentReference cardRef = firestore.collection("managers")
                                .document(managerId)
                                .collection("card_inventory")
                                .document(cardInstanceId);

                        applyCard = FirestoreUtils.monoFromApiFuture(cardRef.get())
                                .flatMap(cardSnapshot -> {
                                    if (!cardSnapshot.exists()) return Mono.error(new RuntimeException("Card not found"));
                                    CardInstance cardInstance = cardSnapshot.toObject(CardInstance.class);
                                    String cardId = cardInstance.getCardId();

                                    return FirestoreUtils.monoFromApiFuture(
                                                    firestore.collection("training_cards").document(cardId).get())
                                            .flatMap(cardDefSnap -> {
                                                if (!cardDefSnap.exists()) return Mono.error(new RuntimeException("Training card not found"));
                                                TrainingCard card = cardDefSnap.toObject(TrainingCard.class);

                                                switch (card.getType()) {
                                                    case "DOUBLE_BOOST":
                                                        baseBoost[0] *= 2;
                                                        break;
                                                    case "BOOST_PERCENT":
                                                        baseBoost[0] *= 1.10;
                                                        break;
                                                    case "MAX_BOOST":
                                                        baseBoost[0] = Math.ceil(baseBoost[0] + 1);
                                                        break;
                                                    case "YOUTH_BONUS":
                                                        if (age < 21) baseBoost[0] += 0.5;
                                                        break;
                                                    case "FITNESS_BONUS":
                                                        if (toDouble(current.getFitnessLevel()) > 90) baseBoost[0] *= 2;
                                                        break;
                                                    case "MORALE_BONUS":
                                                        if (toDouble(current.getMoralLevel()) > 80) baseBoost[0] *= 1.15;
                                                        break;
                                                }

                                                // Update card usage
                                                int remaining = cardInstance.getUsesLeft() - 1;
                                                if (remaining <= 0) {
                                                    return FirestoreUtils.monoFromApiFuture(cardRef.delete()).then();
                                                } else {
                                                    Map<String, Object> cardUpdate = new HashMap<>();
                                                    cardUpdate.put("usesLeft", remaining);
                                                    return FirestoreUtils.monoFromApiFuture(cardRef.update(cardUpdate)).then();
                                                }
                                            });
                                });
                    }

                    // Continue after card logic
                    return applyCard.then(Mono.defer(() -> {
                        boolean grantExtra = age <= 22 && Math.random() < 0.15;
                        if (grantExtra || (age <= 22 && Math.random() < 0.2)) {
                            baseBoost[0] += 1;
                        }

                        current.setExpectedBoost(baseBoost[0]);
                        current.setLastTrainedAt(now);
                        player.setCurrentStats(current);

                        String trainingPath = "slots." + slotId + ".trainingSlots.slot_" + slotIndex;

                        return playerRepository.save(player)
                                .then(FirestoreUtils.monoFromApiFuture(
                                        firestore.collection("managers")
                                                .document(managerId)
                                                .update(trainingPath, player.getPlayerId())
                                )).then();
                    }));
                })
                .then(rewardService.checkAndGrantRewards(managerId))
                .then();
    }

    public Mono<Void> completeTraining(String playerId, String managerId, @Nullable String cardInstanceId, String slotId, int slotIndex) {

        return playerRepository.findById(playerId)
                .switchIfEmpty(Mono.error(new RuntimeException("Player not found")))
                .flatMap(player -> {
                    Player.Stats current = player.getCurrentStats();
                    if (current == null) return Mono.error(new RuntimeException("Current stats not found"));

                    double pendingBoost = toDouble(current.getPendingBoost());
                    double expectedBoost = toDouble(current.getExpectedBoost());
                    double[] totalBoost = new double[]{ pendingBoost + expectedBoost };

                    final double[] attack = {toInt(current.getAttackValue())};
                    final double[] defense = {toInt(current.getDefenseValue())};

                    Mono<Void> applyCardEffects = Mono.empty();

                    if (cardInstanceId != null && !cardInstanceId.isEmpty()) {
                        DocumentReference cardRef = firestore.collection("managers")
                                .document(managerId)
                                .collection("card_inventory")
                                .document(cardInstanceId);

                        applyCardEffects = FirestoreUtils.monoFromApiFuture(cardRef.get())
                                .flatMap(cardSnapshot -> {
                                    if (!cardSnapshot.exists()) return Mono.error(new RuntimeException("Card not found"));
                                    CardInstance cardInstance = cardSnapshot.toObject(CardInstance.class);
                                    String cardId = cardInstance.getCardId();

                                    return FirestoreUtils.monoFromApiFuture(
                                                    firestore.collection("training_cards")
                                                            .document(cardId).get())
                                            .flatMap(cardDefSnap -> {
                                                if (!cardDefSnap.exists()) return Mono.error(new RuntimeException("Training card definition not found"));
                                                TrainingCard card = cardDefSnap.toObject(TrainingCard.class);

                                                switch (card.getType()) {
                                                    case "POSITION_BONUS": totalBoost[0] += 1; break;
                                                    case "COMBO_SUPPORT": totalBoost[0] += 0.5; break;
                                                    case "ELITE_FOCUS":
                                                        if (player.getAge() >= 28) totalBoost[0] += 0.25;
                                                        break;
                                                }

                                                int remaining = cardInstance.getUsesLeft() - 1;
                                                if (remaining <= 0) {
                                                    return FirestoreUtils.monoFromApiFuture(cardRef.delete()).then();
                                                } else {
                                                    Map<String, Object> update = Map.of("usesLeft", remaining);
                                                    return FirestoreUtils.monoFromApiFuture(cardRef.update(update)).then();
                                                }
                                            });
                                });
                    }

                    return applyCardEffects.then(Mono.defer(() -> {
                        double adjustedBoost =  Math.floor(totalBoost[0]);
                        double remaining = totalBoost[0] - adjustedBoost;

                        double marketBoostPerPoint;
                        switch (player.getPosition()) {
                            case "Forward": attack[0] += adjustedBoost; marketBoostPerPoint = 0.15; break;
                            case "Midfielder":
                                attack[0] += adjustedBoost / 2.0;
                                defense[0] += adjustedBoost / 2.0;
                                marketBoostPerPoint = 0.12;
                                break;
                            case "Defender": defense[0] += adjustedBoost; marketBoostPerPoint = 0.10; break;
                            case "Goalkeeper": defense[0] += adjustedBoost; marketBoostPerPoint = 0.08; break;
                            default: marketBoostPerPoint = 0.10; break;
                        }

                        int roundedAttack = (int) Math.round(attack[0]);
                        int roundedDefense = (int) Math.round(defense[0]);
                        int newOverall = (int) Math.round((roundedAttack + roundedDefense) / 2.0);
                        int previousOverall = toInt(current.getOverallValue());
                        double oldMarketValue = parseMarketValue(current.getMarketValue());
                        double newMarketValue = oldMarketValue + ((newOverall - previousOverall) * marketBoostPerPoint);
                        String newMarketStr = formatMarketValue(newMarketValue);

                        current.setAttackValue(roundedAttack);
                        current.setDefenseValue(roundedDefense);
                        current.setOverallValue(newOverall);
                        current.setMarketValue(newMarketStr);
                        current.setPendingBoost(remaining);
                        current.setExpectedBoost(0);

                        player.setCurrentStats(current);

                        String trainingSlotPath = String.format("slots.%s.trainingSlots.slot_%d", slotId, slotIndex);

                        System.out.println("Saving player with pendingBoost=" + current.getPendingBoost());

                        return playerRepository.save(player)
                                .then(FirestoreUtils.monoFromApiFuture(
                                        firestore.collection("managers")
                                                .document(managerId)
                                                .update(
                                                        trainingSlotPath, FieldValue.delete(),
                                                        "trainingCountToday", FieldValue.increment(1),
                                                        "totalTrainings", FieldValue.increment(1)
                                                )
                                ).then());
                    }));
                })
                .then(rewardService.checkAndGrantRewards(managerId))
                .then();
    }


    private long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return 0;
    }

    private double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        return 0.0;
    }

    public double calculateBaseBoost(int age) {
        if (age <= 18) return 0.60 + Math.random() * 0.05; // 0.60–0.65
        if (age <= 21) return 0.45 + Math.random() * 0.15; // 0.45–0.60
        if (age <= 24) return 0.30 + Math.random() * 0.10; // 0.30–0.40
        if (age <= 28) return 0.20 + Math.random() * 0.10; // 0.20–0.30
        return 0.10 + Math.random() * 0.05;                // 0.10–0.15
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private double parseMarketValue(String value) {
        try {
            return Double.parseDouble(value.replace("$", "").replace("M", ""));
        } catch (Exception e) {
            return 0.5;
        }
    }

    private String formatMarketValue(double value) {
        return String.format("$%.1fM", value);
    }

    public Mono<Void> reduceCooldown(String playerId, String managerId, @Nullable String cardInstanceId) {

        long defaultReduction = 2 * 60 * 60 * 1000L; // default 2 hours
        long[] reductionMillis = new long[]{ defaultReduction };

        Mono<Void> cardLogic = Mono.empty();

        if (cardInstanceId != null && !cardInstanceId.isEmpty()) {
            DocumentReference cardRef = firestore.collection("managers")
                    .document(managerId)
                    .collection("card_inventory")
                    .document(cardInstanceId);

            cardLogic = FirestoreUtils.monoFromApiFuture(cardRef.get())
                    .flatMap(cardSnapshot -> {
                        if (!cardSnapshot.exists()) return Mono.error(new RuntimeException("Card not found"));
                        CardInstance instance = cardSnapshot.toObject(CardInstance.class);
                        String cardId = instance.getCardId();

                        return FirestoreUtils.monoFromApiFuture(
                                        firestore.collection("training_cards").document(cardId).get())
                                .flatMap(cardDefSnap -> {
                                    if (!cardDefSnap.exists()) return Mono.error(new RuntimeException("Training card not found"));
                                    TrainingCard card = cardDefSnap.toObject(TrainingCard.class);

                                    if ("REDUCE_COOLDOWN".equals(card.getType())) {
                                        reductionMillis[0] = 2 * 60 * 60 * 1000L; // 2 hours
                                    }

                                    int remaining = instance.getUsesLeft() - 1;
                                    if (remaining <= 0) {
                                        return FirestoreUtils.monoFromApiFuture(cardRef.delete()).then();
                                    } else {
                                        Map<String, Object> update = new HashMap<>();
                                        update.put("usesLeft", remaining);
                                        return FirestoreUtils.monoFromApiFuture(cardRef.update(update)).then();
                                    }
                                });
                    });
        }

        return cardLogic.then(
                playerRepository.findById(playerId)
                        .switchIfEmpty(Mono.error(new RuntimeException("Player not found")))
                        .flatMap(player -> {
                            Player.Stats currentStats = player.getCurrentStats();
                            if (currentStats == null) {
                                return Mono.error(new RuntimeException("Current stats not found"));
                            }

                            long lastTrainedAt = toLong(currentStats.getLastTrainedAt());
                            long newLastTrainedAt = lastTrainedAt - reductionMillis[0];

                            // Update player
                            currentStats.setLastTrainedAt(newLastTrainedAt);
                            player.setCurrentStats(currentStats);

                            return playerRepository.save(player)
                                    .then(FirestoreUtils.monoFromApiFuture(
                                            firestore.collection("managers")
                                                    .document(managerId)
                                                    .update("adsWatched", FieldValue.increment(1))
                                    ).then());
                        })
        );
    }

    public Mono<Void> skipCooldown(String playerId, String managerId, @Nullable String cardInstanceId, int coinCost) {

        boolean[] usedCard = {false};
        Mono<Void> cardLogic = Mono.empty();

        if (cardInstanceId != null && !cardInstanceId.isEmpty()) {
            usedCard[0] = true;
            DocumentReference cardRef = firestore.collection("managers")
                    .document(managerId)
                    .collection("card_inventory")
                    .document(cardInstanceId);

            cardLogic = FirestoreUtils.monoFromApiFuture(cardRef.get())
                    .flatMap(cardSnapshot -> {
                        if (!cardSnapshot.exists()) return Mono.error(new RuntimeException("Card not found"));
                        CardInstance cardInstance = cardSnapshot.toObject(CardInstance.class);
                        String cardId = cardInstance.getCardId();

                        return FirestoreUtils.monoFromApiFuture(
                                        firestore.collection("training_cards").document(cardId).get())
                                .flatMap(cardDefSnap -> {
                                    if (!cardDefSnap.exists()) return Mono.error(new RuntimeException("Training card definition not found"));
                                    TrainingCard card = cardDefSnap.toObject(TrainingCard.class);

                                    if (!"INSTANT_FINISH".equals(card.getType())) {
                                        return Mono.error(new RuntimeException("Invalid card type for skip"));
                                    }

                                    int remaining = cardInstance.getUsesLeft() - 1;
                                    if (remaining <= 0) {
                                        return FirestoreUtils.monoFromApiFuture(cardRef.delete()).then();
                                    } else {
                                        Map<String, Object> update = new HashMap<>();
                                        update.put("usesLeft", remaining);
                                        return FirestoreUtils.monoFromApiFuture(cardRef.update(update)).then();
                                    }
                                });
                    });
        }

        return cardLogic.then(
                FirestoreUtils.monoFromApiFuture(
                                firestore.collection("managers").document(managerId).get()
                        )
                        .flatMap(managerSnapshot -> {
                            if (!managerSnapshot.exists()) return Mono.error(new RuntimeException("Manager not found"));

                            long coins = managerSnapshot.getLong("coins");
                            if (!usedCard[0] && coins < coinCost) {
                                return Mono.error(new RuntimeException("Not enough coins"));
                            }

                            return playerRepository.findById(playerId)
                                    .switchIfEmpty(Mono.error(new RuntimeException("Player not found")))
                                    .flatMap(player -> {
                                        Player.Stats currentStats = player.getCurrentStats();
                                        if (currentStats == null) {
                                            return Mono.error(new RuntimeException("Current stats not found"));
                                        }

                                        // Skip cooldown by setting lastTrainedAt = 0
                                        currentStats.setLastTrainedAt(0L);
                                        player.setCurrentStats(currentStats);

                                        // Save updated player
                                        return playerRepository.save(player)
                                                .then(Mono.defer(() -> {
                                                    Map<String, Object> managerUpdates = new HashMap<>();
                                                    if (!usedCard[0]) {
                                                        managerUpdates.put("coins", coins - coinCost);
                                                        managerUpdates.put("cooldownSkips", FieldValue.increment(1));
                                                    }
                                                    // Update manager Firestore data
                                                    return FirestoreUtils.monoFromApiFuture(
                                                            firestore.collection("managers")
                                                                    .document(managerId)
                                                                    .update(managerUpdates)
                                                    ).then();
                                                }));
                                    });
                        })
        ).then(rewardService.checkAndGrantRewards(managerId)).then();
    }

    public Mono<Void> applyCardToPlayer(String managerId, String playerId, String cardInstanceId) {

        DocumentReference cardRef = firestore.collection("managers")
                .document(managerId)
                .collection("card_inventory")
                .document(cardInstanceId);

        return FirestoreUtils.monoFromApiFuture(cardRef.get())
                .flatMap(cardSnap -> {
                    if (!cardSnap.exists()) return Mono.error(new RuntimeException("Card not found"));
                    CardInstance instance = cardSnap.toObject(CardInstance.class);
                    assert instance != null;
                    String cardId = instance.getCardId();

                    return FirestoreUtils.monoFromApiFuture(
                                    firestore.collection("training_cards").document(cardId).get())
                            .flatMap(defSnap -> {
                                if (!defSnap.exists()) return Mono.error(new RuntimeException("Training card definition not found"));
                                TrainingCard card = defSnap.toObject(TrainingCard.class);

                                return playerRepository.findById(playerId)
                                        .switchIfEmpty(Mono.error(new RuntimeException("Player not found")))
                                        .flatMap(player -> {
                                            Player.Stats stats = player.getCurrentStats();
                                            if (stats == null) return Mono.error(new RuntimeException("Current stats not found"));

                                            int age = player.getAge();
                                            double boost = 0;
                                            boolean shouldApplyCard = true;

                                            switch (Objects.requireNonNull(card).getType()) {
                                                case "DOUBLE_BOOST":
                                                    boost = calculateBaseBoost(age) * 2;
                                                    break;
                                                case "BOOST_PERCENT":
                                                    boost = calculateBaseBoost(age) * 1.10;
                                                    break;
                                                case "YOUTH_BONUS":
                                                    boost = age < 21 ? calculateBaseBoost(age) + 0.5 : 0;
                                                    break;
                                                case "MAX_BOOST":
                                                    boost = Math.ceil(calculateBaseBoost(age) + 1);
                                                    break;
                                                case "POSITION_BONUS":
                                                    boost = 1; // Frontend decides if valid
                                                    break;
                                                case "FITNESS_BONUS":
                                                    if (toDouble(stats.getFitnessLevel()) >= 100) shouldApplyCard = false;
                                                    else stats.setFitnessLevel(100);
                                                    break;
                                                case "REMOVE_INJURY":
                                                    if (!"injured".equals(player.getStatus())) shouldApplyCard = false;
                                                    else player.setStatus("available");
                                                    break;
                                                case "MORALE_BONUS":
                                                    if (toDouble(stats.getMoralLevel()) >= 100) shouldApplyCard = false;
                                                    else stats.setMoralLevel(100);
                                                    break;
                                                case "AGING_PROTECTION":
                                                    //stats.put("agingProtected", true);
                                                    break;
                                            }

                                            if (!shouldApplyCard) {
                                                return Mono.error(new RuntimeException("Card effect is not applicable"));
                                            }

                                            double existingExpected = toDouble(stats.getExpectedBoost());
                                            stats.setExpectedBoost( existingExpected + boost);

                                            player.setCurrentStats(stats);

                                            return playerRepository.save(player)
                                                    .then(cardService.useCard(managerId, cardInstanceId));
                                        });
                            });
                });
    }

}
