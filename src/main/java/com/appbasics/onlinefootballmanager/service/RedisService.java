package com.appbasics.onlinefootballmanager.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class RedisService {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Mono<Boolean> set(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Mono<Boolean> delete(String key) {
        return redisTemplate.delete(key).map(deleted -> deleted > 0);
    }

    public Mono<Boolean> exists(String key) {
        return redisTemplate.hasKey(key);
    }
}
