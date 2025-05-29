package com.appbasics.onlinefootballmanager.repository.mongo;

import com.appbasics.onlinefootballmanager.model.Player;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface PlayerRepository extends ReactiveMongoRepository<Player, String> {
    Flux<Player> findByInstanceIdAndTeamId(String instanceId, String teamId);
    //Flux<Player> findAllByManagerId(String managerId);
    List<Player> findByTeamId(String teamId);
    Flux<Player> findByInstanceId(String instanceId);
    Mono<Player> findByPlayerId(String playerId);
    Flux<Player> findByPlayerIdIn(List<String> playerIds);
    Flux<Player> findByRegionIdAndStatus(String regionId, String status);

}