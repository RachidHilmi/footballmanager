package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.LeagueInstance;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LeagueInstanceRepository extends ReactiveMongoRepository<LeagueInstance, String> {
    Flux<LeagueInstance> findByRegionId(String regionId);
    Flux<LeagueInstance> findByStatus(String status);
    Mono<LeagueInstance> findByInstanceId(String instanceId);
    Mono<LeagueInstance> findFirstByInstanceId(String instanceId);
    Flux<LeagueInstance> findByLeagueId(String leagueId);
    Flux<LeagueInstance> findByTemplateId(String templateId);

}
