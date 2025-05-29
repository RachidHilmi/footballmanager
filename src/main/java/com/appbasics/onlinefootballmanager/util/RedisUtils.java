package com.appbasics.onlinefootballmanager.util;

import com.appbasics.onlinefootballmanager.model.TransferListing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public class RedisUtils {

    public static Mono<List<String>> getAll(RedisTemplate<String, String> redisTemplate, String key) {
        return Mono.justOrEmpty(redisTemplate.opsForList().range(key, 0, -1));
    }

    public static Mono<Long> getSize(RedisTemplate<String, String> redisTemplate, String key) {
        return Mono.justOrEmpty(redisTemplate.opsForList().size(key));
    }

    public static Mono<Void> removeAndReplace(RedisTemplate<String, String> redisTemplate,
                                              String listKey,
                                              String listingId,
                                              TransferListing updated,
                                              ObjectMapper objectMapper) {
        return getAll(redisTemplate, listKey)
                .flatMapMany(Flux::fromIterable)
                .flatMap(json -> {
                    try {
                        TransferListing listing = objectMapper.readValue(json, TransferListing.class);
                        if (listing.getId().equals(listingId)) {
                            redisTemplate.opsForList().remove(listKey, 1, json);
                            String newJson = objectMapper.writeValueAsString(updated);
                            redisTemplate.opsForList().rightPush(listKey, newJson);
                        }
                        return Mono.empty();
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .then();
    }

    public static Mono<Void> removeFromList(RedisTemplate<String, String> redisTemplate, String key, String listingId) {
        return getAll(redisTemplate, key)
                .flatMapMany(Flux::fromIterable)
                .index()
                .flatMap(entry -> {
                    long index = entry.getT1();
                    String json = entry.getT2();
                    try {
                        TransferListing listing = new ObjectMapper().readValue(json, TransferListing.class);
                        if (listing.getId().equals(listingId)) {
                            redisTemplate.opsForList().set(key, index, "DELETED");
                        }
                    } catch (Exception ignored) {}
                    return Mono.empty();
                }).then(Mono.fromRunnable(() -> redisTemplate.opsForList().remove(key, 0, "DELETED")));
    }

}
