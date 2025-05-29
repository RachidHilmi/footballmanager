package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.MatchReplay;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MatchReplayRepository extends ReactiveMongoRepository<MatchReplay, String> {
    Mono<MatchReplay> findByMatchId(String matchId);
    @Query("{'_id': { $regex: ?0 }}")
    Mono<Void> deleteAllByMatchIdRegex(String regex);
}
