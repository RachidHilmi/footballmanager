package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.CardInstance;
import com.appbasics.onlinefootballmanager.model.TrainingCard;
import com.appbasics.onlinefootballmanager.util.FirestoreUtils;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardService {

    private final Firestore firestore;

    public Mono<List<TrainingCard>> getAllCardTypes() {
        CollectionReference ref = firestore.collection("training_cards");

        return FirestoreUtils.monoFromApiFuture(ref.get())
                .map(snapshot -> snapshot.getDocuments().stream()
                        .map(doc -> doc.toObject(TrainingCard.class))
                        .collect(Collectors.toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<CardInstance>> getManagerCards(String managerId) {
        CollectionReference inventoryRef = firestore.collection("managers")
                .document(managerId)
                .collection("card_inventory");

        return FirestoreUtils.monoFromApiFuture(inventoryRef.get())
                .flatMap(snapshot -> {
                    List<CardInstance> instances = snapshot.getDocuments().stream()
                            .map(doc -> {
                                CardInstance instance = doc.toObject(CardInstance.class);
                                instance.setInstanceId(doc.getId());
                                return instance;
                            })
                            .collect(Collectors.toList());

                    List<Mono<CardInstance>> enriched = instances.stream().map(instance -> {
                        return FirestoreUtils.monoFromApiFuture(
                                firestore.collection("training_cards").document(instance.getCardId()).get()
                        ).map(cardSnap -> {
                            if (cardSnap.exists()) {
                                TrainingCard def = cardSnap.toObject(TrainingCard.class);
                                assert def != null;
                                instance.setType(def.getType());
                            }
                            return instance;
                        });
                    }).toList();

                    return Flux.merge(enriched).collectList();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }


    public Mono<Void> useCard(String managerId, String instanceId) {
        DocumentReference docRef = firestore.collection("managers")
                .document(managerId)
                .collection("card_inventory")
                .document(instanceId);

        return FirestoreUtils.monoFromApiFuture(docRef.get())
                .flatMap(snapshot -> {
                    if (!snapshot.exists()) {
                        return Mono.error(new RuntimeException("Card not found"));
                    }

                    CardInstance card = snapshot.toObject(CardInstance.class);
                    if (card.getUsesLeft() <= 0) {
                        return Mono.error(new RuntimeException("Card already used"));
                    }
                    if (card.getUsesLeft() == 1) {
                        return FirestoreUtils.monoFromApiFuture(docRef.delete()).then();
                    } else {
                        Map<String, Object> update = new HashMap<>();
                        update.put("usesLeft", card.getUsesLeft() - 1);
                        return FirestoreUtils.monoFromApiFuture(docRef.update(update)).then();
                    }
                });
    }

    public Mono<Void> addCardToManager(String managerId, CardInstance instance) {
        String docId = UUID.randomUUID().toString();
        instance.setInstanceId(docId);

        return FirestoreUtils.monoFromApiFuture(
                firestore.collection("managers")
                        .document(managerId)
                        .collection("card_inventory")
                        .document(docId)
                        .set(instance)
        ).then();
    }
}