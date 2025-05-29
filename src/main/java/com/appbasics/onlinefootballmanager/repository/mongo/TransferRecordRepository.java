package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.TransferRecord;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface TransferRecordRepository extends ReactiveMongoRepository<TransferRecord, String> {
    Mono<Void> deleteAllByNewInstanceId(String instanceId);
}
