package com.appbasics.onlinefootballmanager.repository.firestore;

import com.appbasics.onlinefootballmanager.model.Manager;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ManagerRepository  extends FirestoreReactiveRepository<Manager> {
    Mono<Manager> findByManagerId(String managerId);
    Mono<Manager> findByName(String name);
    Mono<Manager> findByEmail(String email);
}