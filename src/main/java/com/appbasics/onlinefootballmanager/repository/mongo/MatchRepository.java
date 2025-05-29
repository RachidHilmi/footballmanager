package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.Match;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MatchRepository extends ReactiveMongoRepository<Match, String> {
    Mono<Match> findByInstanceIdAndMatchId(String instanceId, String matchId);
    Flux<Match> findAllByInstanceIdAndMatchday(String instanceId, int matchday);
    Flux<Match> findAllByInstanceId(String instanceId);
    Mono<Void> deleteAllByInstanceId(String instanceId);

}
