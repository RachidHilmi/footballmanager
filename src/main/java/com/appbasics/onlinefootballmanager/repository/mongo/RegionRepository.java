package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.Region;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;


@Repository
public interface RegionRepository extends ReactiveMongoRepository<Region, String> {
    Flux<Region> findByRegionId(String regionId);
}
