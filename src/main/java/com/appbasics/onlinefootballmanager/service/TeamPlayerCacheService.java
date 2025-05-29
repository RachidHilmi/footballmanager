package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.Player;
import com.appbasics.onlinefootballmanager.repository.mongo.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class TeamPlayerCacheService {

    @Autowired
    private PlayerCacheService playerCacheService;
    @Autowired
    private PlayerRepository playerRepository;


    public Mono<List<Player>> getTeamPlayers(String regionId, String instanceId, String teamId) {
        String cacheKey = "teamPlayers:" + regionId + ":" + instanceId + ":" + teamId;

        List<Player> cachedPlayers = playerCacheService.getCachedTeamPlayers(cacheKey);

        if (cachedPlayers != null && !cachedPlayers.isEmpty()) {
            return Mono.just(cachedPlayers);
        }

        return playerRepository.findByInstanceIdAndTeamId(instanceId, teamId)
                .collectList()
                .flatMap(players -> {
                    if (players != null && !players.isEmpty()) {
                        playerCacheService.cacheTeamPlayers(cacheKey, players);
                    }
                    assert players != null;
                    return Mono.just(players);
                });
    }

    public Mono<List<Player>> getPlayersByIds(List<String> playerIds) {
        return playerRepository.findByPlayerIdIn(playerIds)
                .collectList();
    }

}
