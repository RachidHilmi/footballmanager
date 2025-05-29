package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.LeagueTemplate;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface LeagueTemplateRepository extends ReactiveMongoRepository<LeagueTemplate, String> {
    Flux<LeagueTemplate> findByRegionId(String regionId);
}
