package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.Standing;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface StandingsRepository extends ReactiveMongoRepository<Standing, String> {

    default Flux<Standing> findByInstanceIdOrderByPointsDescGoalDifferenceDesc(String instanceId){
        Sort sort = Sort.by(
                Sort.Order.desc("points"),
                Sort.Order.desc("goalDifference"),
                Sort.Order.desc("goalsFor"));
        return findByInstanceId(instanceId, sort);
    }

    Flux<Standing> findByInstanceId(String instanceId, Sort sort);

    default Flux<Standing> findByInstanceIdOrderByTeamIdAsc(String instanceId) {
        Sort sort = Sort.by(Sort.Order.asc("teamId"));
        return findByInstanceId(instanceId, sort);
    }

    Mono<Standing> findByInstanceIdAndTeamId(String instanceId, String teamId);
    Flux<Standing> findByInstanceId(String instanceId);
}




