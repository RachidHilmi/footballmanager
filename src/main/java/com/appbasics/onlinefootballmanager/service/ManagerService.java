package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.Manager;
import com.appbasics.onlinefootballmanager.model.Slot;
import com.appbasics.onlinefootballmanager.model.Trophy;
import com.appbasics.onlinefootballmanager.repository.firestore.ManagerRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.util.FirestoreUtils;
import com.appbasics.onlinefootballmanager.util.RegisterResponse;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ManagerService {
    @Autowired
    private Firestore firestore;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ManagerRepository managerRepository;
    @Autowired
    private LeagueInstanceRepository leagueInstanceRepository;

    public Mono<RegisterResponse> registerManager(Manager manager) {
        CollectionReference managers = firestore.collection("managers");

        // Check if name is already taken asynchronously
        ApiFuture<QuerySnapshot> queryFuture = managers.whereEqualTo("name", manager.getName()).get();
        return FirestoreUtils.monoFromApiFuture(queryFuture)
                .flatMap(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        return Mono.error(new RuntimeException("Name is already in use"));
                    }

                    String generatedId = UUID.randomUUID().toString();
                    manager.setId(generatedId);
                    manager.setManagerId(generatedId);

                    // Hash the password before storing
                    manager.setPassword(passwordEncoder.encode(manager.getPassword()));

                    // Initialize default values for game-specific fields
                    manager.setDominationPoints(0);
                    manager.setManagerPoints(0);
                    manager.setRanking(0);
                    manager.setCoins(0);
                    manager.setLeaguesWon(0);
                    manager.setCupsWon(0);
                    manager.setObjectivesCompleted(0);
                    manager.setActiveSlot("");
                    manager.setNationality("N/A");
                    manager.setChatReferences(new ArrayList<>());

                    Map<String, Slot> defaultSlots = new HashMap<>();
                    for (int i = 1; i <= 4; i++) {
                        String slotId = "slot_" + i;
                        defaultSlots.put(slotId, new Slot(slotId, true, "", "", "", "", "", "","",  null));
                    }
                    manager.setSlots(defaultSlots);

                    // Save to Firestore asynchronously
                    ApiFuture<WriteResult> writeFuture = managers.document(generatedId).set(manager);
                    String token;
                    try {
                        token = FirebaseAuth.getInstance().createCustomToken(manager.getId());
                    } catch (FirebaseAuthException e) {
                        return Mono.error(e);
                    }
                    return FirestoreUtils.monoFromApiFuture(writeFuture)
                            .thenReturn(new RegisterResponse(generatedId, token));
                }).subscribeOn(Schedulers.boundedElastic());
    }


    public Mono<List<Trophy>> getManagerTrophies(String managerId) {
        CollectionReference trophyCollection = firestore.collection("managers")
                .document(managerId)
                .collection("trophy");

        ApiFuture<QuerySnapshot> queryFuture = trophyCollection.get();

        return FirestoreUtils.monoFromApiFuture(queryFuture)
                .map(querySnapshot -> querySnapshot.getDocuments()
                        .stream()
                        .map(document -> document.toObject(Trophy.class))
                        .collect(Collectors.toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> login(String name, String password) {
        CollectionReference managers = firestore.collection("managers");

        ApiFuture<QuerySnapshot> queryFuture = managers.whereEqualTo("name", name).get();
        return FirestoreUtils.monoFromApiFuture(queryFuture)
                .flatMap(snapshot -> {
                    if (snapshot.getDocuments().isEmpty()) {
                        return Mono.error(new RuntimeException("Invalid credentials"));
                    }
                    Manager manager = snapshot.getDocuments().get(0).toObject(Manager.class);
                    if (!passwordEncoder.matches(password, manager.getPassword())) {
                        return Mono.error(new RuntimeException("Invalid credentials"));
                    }
                    if (manager.getId() == null || manager.getId().isEmpty()) {
                        return Mono.error(new RuntimeException("Manager ID is missing. Registration might be incorrect."));
                    }
                    try {
                        String token = FirebaseAuth.getInstance().createCustomToken(manager.getId());
                        System.out.println("Generated Firebase token for manager ID: " + manager.getId());
                        System.out.println("Token: " + token);
                        return Mono.just(token);
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
