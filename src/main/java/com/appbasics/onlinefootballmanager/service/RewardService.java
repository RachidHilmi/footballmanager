package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.CardInstance;
import com.appbasics.onlinefootballmanager.model.TrainingCard;
import com.appbasics.onlinefootballmanager.util.FirestoreUtils;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RewardService {

    private final Firestore firestore;
    private final CardService cardService;

    public Mono<List<CardInstance>> checkAndGrantRewards(String managerId) {
        List<CardInstance> grantedCards = new ArrayList<>();

            return FirestoreUtils.monoFromApiFuture(firestore.collection("managers").document(managerId).get())
                .flatMap(snapshot -> {
                    if (!snapshot.exists()) return Mono.just(Collections.emptyList());

                    Map<String, Object> data = snapshot.getData();
                    if (data == null) return Mono.just(Collections.emptyList());

                    // Example reward checks
                    int trainToday = ((Number) data.getOrDefault("trainingCountToday", 0)).intValue();
                    int totalTrain = ((Number) data.getOrDefault("totalTrainings", 0)).intValue();
                    int cooldownSkips = ((Number) data.getOrDefault("cooldownSkips", 0)).intValue();
                    int adsWatched = ((Number) data.getOrDefault("adsWatched", 0)).intValue();
                    boolean firstLogin = Boolean.TRUE.equals(data.get("firstLoginRewardGiven")) == false;
                    int cooldownLevel = ((Number) data.getOrDefault("cooldownSkipsRewardLevel", 0)).intValue();
                    int adsWatchedLevel = ((Number) data.getOrDefault("adsWatchedRewardLevel", 0)).intValue();
                    int totalTrainLevel = ((Number) data.getOrDefault("totalTrainRewardLevel", 0)).intValue();

                    List<Mono<Void>> additions = new ArrayList<>();

                    if (firstLogin) {
                        additions.add(grantCards(managerId, "COMMON", 2, "first_login", grantedCards));
                        additions.add(updateFlag(managerId, "firstLoginRewardGiven", true));
                    }

//                    if (resetTrainCount) {
//                        additions.add(updateIntFlag(managerId, "trainingCountToday", 0));
//                    } else

                    if (trainToday == 16) {
                        additions.add(grantCards(managerId, "COMMON", 1, "train_16_player_day", grantedCards));
                        additions.add(updateIntFlag(managerId, "trainingCountToday", 0));
                    }

                    if (totalTrain >= 500 && totalTrainLevel < 3) {
                        additions.add(grantCards(managerId, "LEGENDARY", 1, "500_trainings", grantedCards));
                        additions.add(updateIntFlag(managerId, "totalTrainRewardLevel", 3));
                    } else if (totalTrain >= 250 && totalTrainLevel < 2) {
                        additions.add(grantCards(managerId, "COMMON", 1, "250_trainings", grantedCards));
                        additions.add(updateIntFlag(managerId, "totalTrainRewardLevel", 2));
                    } else if (totalTrain >= 100 && totalTrainLevel < 1) {
                        additions.add(grantCards(managerId, "COMMON", 1, "100_trainings", grantedCards));
                        additions.add(updateIntFlag(managerId, "totalTrainRewardLevel", 1));
                    } else if (totalTrain >= 750 && totalTrainLevel < 4) {
                        additions.add(grantCards(managerId, "RARE", 1, "750_trainings", grantedCards));
                        additions.add(updateIntFlag(managerId, "totalTrainRewardLevel", 4));
                    }

                    if (cooldownSkips >= 100 && cooldownLevel < 3) {
                        additions.add(grantCards(managerId, "LEGENDARY", 1, "100_skips", grantedCards));
                        additions.add(updateIntFlag(managerId, "cooldownSkipsRewardLevel", 3));
                    } else if (cooldownSkips >= 50 && cooldownLevel < 2) {
                        additions.add(grantCards(managerId, "COMMON", 1, "50_skips", grantedCards));
                        additions.add(updateIntFlag(managerId, "cooldownSkipsRewardLevel", 2));
                    } else if (cooldownSkips >= 10 && cooldownLevel < 1) {
                        additions.add(grantCards(managerId, "COMMON", 1, "10_skips", grantedCards));
                        additions.add(updateIntFlag(managerId, "cooldownSkipsRewardLevel", 1));
                    } else if (cooldownSkips >= 150 && cooldownLevel < 4) {
                        additions.add(grantCards(managerId, "RARE", 1, "150_skips", grantedCards));
                        additions.add(updateIntFlag(managerId, "cooldownSkipsRewardLevel", 4));
                    }

                    if (adsWatched >= 100 && adsWatchedLevel < 3) {
                        additions.add(grantCards(managerId, "LEGENDARY", 1, "100_ads", grantedCards));
                        additions.add(updateIntFlag(managerId, "adsWatchedRewardLevel", 3));
                    } else if (adsWatched >= 50 && adsWatchedLevel < 2) {
                        additions.add(grantCards(managerId, "COMMON", 1, "50_ads", grantedCards));
                        additions.add(updateIntFlag(managerId, "adsWatchedRewardLevel", 2));
                    } else if (adsWatched >= 10 && adsWatchedLevel < 1) {
                        additions.add(grantCards(managerId, "COMMON", 1, "10_ads", grantedCards));
                        additions.add(updateIntFlag(managerId, "adsWatchedRewardLevel", 1));
                    } else if (adsWatched >= 150 && adsWatchedLevel < 4) {
                        additions.add(grantCards(managerId, "RARE", 1, "150_ads", grantedCards));
                        additions.add(updateIntFlag(managerId, "adsWatchedRewardLevel", 4));
                    }

                    return Mono.when(additions).thenReturn(grantedCards);
                });
        //});
    }

    public Mono<Void> grantCards(String managerId, String rarity, int count, String source, List<CardInstance> resultList) {
        return cardService.getAllCardTypes()
                .flatMapMany(Flux::fromIterable)
                .filter(card -> rarity.equalsIgnoreCase(card.getRarity()))
                .collectList()
                .flatMap(cards -> {
                    if (cards.isEmpty()) return Mono.empty();

                    List<Mono<Void>> grants = new ArrayList<>();

                    for (int i = 0; i < count; i++) {
                        TrainingCard random = cards.get(ThreadLocalRandom.current().nextInt(cards.size()));
                        String cardId = random.getCardId();

                        Mono<Void> grantMono = FirestoreUtils.monoFromApiFuture(
                                        firestore.collection("managers")
                                                .document(managerId)
                                                .collection("card_inventory")
                                                .whereEqualTo("cardId", cardId)
                                                .limit(1)
                                                .get())
                                .flatMap(snapshot -> {
                                    if (!snapshot.isEmpty()) {
                                        // Card exists, increment usesLeft
                                        var doc = snapshot.getDocuments().get(0);
                                        String docId = doc.getId();
                                        Long currentUses = doc.getLong("usesLeft");

                                        long newUses = (currentUses != null ? currentUses : 0) + random.getMaxUses();
                                        CardInstance instance = new CardInstance();
                                        instance.setInstanceId(UUID.randomUUID().toString());
                                        instance.setCardId(random.getCardId());
                                        instance.setUsesLeft(random.getMaxUses());
                                        instance.setName(random.getName());
                                        instance.setRarity(random.getRarity());
                                        instance.setIcon(random.getIcon());
                                        instance.setAcquiredAt(new Date());
                                        instance.setSource(source);
                                        instance.setCategory(random.getCategory());
                                        instance.setType(random.getType());
                                        resultList.add(instance);

                                        return FirestoreUtils.monoFromApiFuture(
                                                doc.getReference().update("usesLeft", newUses)
                                        ).then();
                                    } else {
                                        // Card doesn't exist, create new
                                        CardInstance instance = new CardInstance();
                                        instance.setInstanceId(UUID.randomUUID().toString());
                                        instance.setCardId(random.getCardId());
                                        instance.setUsesLeft(random.getMaxUses());
                                        instance.setName(random.getName());
                                        instance.setRarity(random.getRarity());
                                        instance.setIcon(random.getIcon());
                                        instance.setAcquiredAt(new Date());
                                        instance.setSource(source);
                                        instance.setCategory(random.getCategory());
                                        instance.setType(random.getType());
                                        resultList.add(instance);

                                        return cardService.addCardToManager(managerId, instance);
                                    }
                                });

                        grants.add(grantMono);
                    }

                    return Mono.when(grants);
                });
    }


    private Mono<Void> updateFlag(String managerId, String field, boolean value) {
        return FirestoreUtils.monoFromApiFuture(
                firestore.collection("managers").document(managerId)
                        .update(field, value)
        ).then();
    }

    private Mono<Void> updateIntFlag(String managerId, String field, int value) {
        return FirestoreUtils.monoFromApiFuture(
                firestore.collection("managers").document(managerId)
                        .update(field, value)
        ).then();
    }

    private String getRandomRarity() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 60) return "COMMON";
        if (roll < 90) return "RARE";
        return "LEGENDARY";
    }
}
