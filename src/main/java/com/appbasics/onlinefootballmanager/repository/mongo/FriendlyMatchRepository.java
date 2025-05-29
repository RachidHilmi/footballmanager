package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.FriendlyMatch;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.Date;

@Repository
public interface FriendlyMatchRepository extends ReactiveMongoRepository<FriendlyMatch, String> {

    Flux<FriendlyMatch> findByInitiatorManagerIdAndPlayedAtBetween(String managerId, Date start, Date end);

    Flux<FriendlyMatch> findByInitiatorManagerIdAndTargetTeamIdAndInstanceIdAndPlayedAtBetween(
            String initiatorManagerId,
            String targetTeamId,
            String instanceId,
            Date start,
            Date end
    );

    Flux<FriendlyMatch> findByInitiatorManagerIdAndInstanceIdAndPlayedAtBetween(
            String initiatorManagerId,
            String instanceId,
            Date from,
            Date to
    );
}
