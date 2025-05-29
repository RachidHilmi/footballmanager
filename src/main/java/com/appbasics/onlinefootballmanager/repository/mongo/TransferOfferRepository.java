package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.TransferOffer;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TransferOfferRepository extends ReactiveMongoRepository<TransferOffer, String> {
    Flux<TransferOffer> findByAcceptedFalseAndRejectedFalseAndOfferedAtLessThan(long offeredAt);
    Flux<TransferOffer> findByRegionIdAndInstanceIdAndAcceptedFalseAndRejectedFalse(String regionId, String instanceId);
    Flux<TransferOffer> findByToTeamIdAndAcceptedFalseAndRejectedFalse(String toTeamId);
    Mono<Void> deleteAllByInstanceId(String instanceId);
}
