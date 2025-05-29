package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PlayerCacheService {

    private static final long CACHE_TTL = 10; // 10 minutes cache

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void cacheTeamPlayers(String cacheKey, List<Player> players) {
        redisTemplate.opsForValue().set(cacheKey, players, CACHE_TTL, TimeUnit.MINUTES);
    }

    public List<Player> getCachedTeamPlayers(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List<?>) {
            return (List<Player>) cached;
        }
        return null;
    }

    public void deleteCache(String cacheKey) {
        redisTemplate.delete(cacheKey);
    }
}
