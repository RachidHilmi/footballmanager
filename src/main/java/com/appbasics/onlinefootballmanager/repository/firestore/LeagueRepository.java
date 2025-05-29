package com.appbasics.onlinefootballmanager.repository.firestore;

import com.appbasics.onlinefootballmanager.model.League;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LeagueRepository extends FirestoreReactiveRepository<League> {
    Mono<League> findByLeagueId(String leagueId);
    Flux<League> findAllByStatus(String status);
    Flux<League> findByRegionIdAndStatus(String regionId, String status);

}

