package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.Player;
import com.appbasics.onlinefootballmanager.repository.mongo.PlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class SystemCandidateService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlayerRepository playerRepository;

    private static final Duration SYSTEM_CANDIDATES_TTL = Duration.ofHours(6);

    private String getSystemCandidatesKey(String regionId) {
        return "system_candidates:" + regionId;
    }

    @PostConstruct
    public void warmUpSystemCandidates() {
        List<String> regions = List.of("Africa", "Asia", "Europe", "Americas");
        for (String region : regions) {
            refreshSystemCandidates(region)
                    .doOnError(e -> System.err.println("Failed to warmup candidates for region: " + region + " error=" + e.getMessage()))
                    .subscribe();
        }
    }

    public Mono<Void> refreshSystemCandidates(String regionId) {
        return playerRepository.findByRegionIdAndStatus(regionId, "available")
                .filter(player -> player.getAge() < 29)
                .map(player -> {
                    try {
                        return objectMapper.writeValueAsString(player);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collectList()
                .flatMap(candidates -> {
                    if (!candidates.isEmpty()) {
                        redisTemplate.delete(getSystemCandidatesKey(regionId));
                        redisTemplate.opsForList().rightPushAll(getSystemCandidatesKey(regionId), candidates);
                        redisTemplate.expire(getSystemCandidatesKey(regionId), SYSTEM_CANDIDATES_TTL);
                    }
                    return Mono.empty();
                });
    }


    public Mono<List<Player>> getBoostedSystemCandidates(String regionId, int neededCount, int avgAttack, int avgDefense, int avgOverall) {

        return getRandomSystemCandidates(regionId, neededCount * 4)
                .flatMapMany(Flux::fromIterable)
                .map(this::boostPlayer)
                .collectList()
                .flatMap(players -> {
                    List<Player> forwards = new ArrayList<>();
                    List<Player> midfielders = new ArrayList<>();
                    List<Player> defenders = new ArrayList<>();
                    List<Player> goalkeepers = new ArrayList<>();

                    for (Player p : players) {
                        if (p.getPosition() == null) continue;
                        String pos = p.getPosition().toLowerCase();

                        if (pos.contains("forward") || pos.contains("striker") || pos.equals("st")) {
                            if (isBetterThanHumanAverage(p, avgAttack, avgDefense, avgOverall)) forwards.add(p);
                        } else if (pos.contains("midfield") || pos.equals("cm") || pos.equals("lm") || pos.equals("rm")) {
                            if (isBetterThanHumanAverage(p, avgAttack, avgDefense, avgOverall)) midfielders.add(p);
                        } else if (pos.contains("defender") || pos.equals("cb") || pos.equals("lb") || pos.equals("rb")) {
                            if (isBetterThanHumanAverage(p, avgAttack, avgDefense, avgOverall)) defenders.add(p);
                        } else if (pos.contains("goalkeeper") || pos.equals("gk") || pos.contains("keeper")) {
                            // Apply relaxed filter for GKs
                            goalkeepers.add(p); // Do NOT filter them aggressively
                        }
                    }

                    List<Player> selected = new ArrayList<>();

                    int perRole = neededCount / 4;

                    selected.addAll(pickRandom(forwards, perRole));
                    selected.addAll(pickRandom(midfielders, perRole));
                    selected.addAll(pickRandom(defenders, perRole));
                    selected.addAll(pickRandom(goalkeepers, perRole));
                    int remaining = neededCount - selected.size();
                    if (remaining > 0) {
                        List<Player> pool = new ArrayList<>();
                        pool.addAll(forwards);
                        pool.addAll(midfielders);
                        pool.addAll(defenders);
                        pool.addAll(goalkeepers);
                        pool.removeAll(selected);
                        Collections.shuffle(pool);
                        selected.addAll(pool.subList(0, Math.min(remaining, pool.size())));
                    }

                    return Mono.just(selected);
                });
    }

    private List<Player> pickRandom(List<Player> list, int count) {
        Collections.shuffle(list);
        return list.subList(0, Math.min(count, list.size()));
    }

    private Player boostPlayer(Player player) {
        String pos = player.getPosition().toLowerCase();
        Player.Stats stats = player.getCurrentStats();

        int attack = stats.getAttackValue();
        int defense = stats.getDefenseValue();

        if (pos.contains("forward")) {
            attack += getRandomBoost(2, 5);
        } else if (pos.contains("midfield")) {
            int boost = getRandomBoost(1, 3);
            attack += boost;
            defense += boost;
        } else if (pos.contains("defender")) {
            defense += getRandomBoost(2, 5);
        } else if (pos.contains("gk") || pos.contains("keeper") || pos.contains("goalkeeper")) {
            defense += getRandomBoost(3, 6);
        } else {
            attack += 2;
            defense += 2;
        }

        int overall = (attack + defense) / 2;
        player.getCurrentStats().setAttackValue(attack);
        player.getCurrentStats().setDefenseValue(defense);
        player.getCurrentStats().setOverallValue(overall);

        return player;
    }

    private boolean isBetterThanHumanAverage(Player player, int avgAttack, int avgDefense, int avgOverall) {
        String pos = player.getPosition().toLowerCase();
        Player.Stats stats = player.getCurrentStats();
        int attack = stats.getAttackValue();
        int defense = stats.getDefenseValue();
        int overall = stats.getOverallValue();

        if (pos.contains("forward")) {
            return attack > avgAttack + 2;
        } else if (pos.contains("midfield")) {
            return attack > avgAttack + 2 && defense > avgDefense + 2 && Math.abs(attack - defense) <= 10;
        } else if (pos.contains("defender")) {
            return defense > avgDefense + 2;
        } else if (pos.contains("keeper") || pos.contains("goalkeeper")) {
            return defense > avgDefense + 2;
        } else {
            return overall > avgOverall + 2;
        }
    }

    private int getRandomBoost(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }

    private Mono<List<Player>> getRandomSystemCandidates(String regionId, int count) {
        String redisKey = getSystemCandidatesKey(regionId);
        return Mono.fromCallable(() -> redisTemplate.opsForList().range(redisKey, 0, -1))
                .retry(1)
                .flatMap(list -> {
                    if (list == null || list.isEmpty()) {
                        return fallbackFetchFromMongo(regionId, count);
                    }
                    Collections.shuffle(list);
                    return Mono.just(list.stream()
                            .limit(count)
                            .map(json -> {
                                try {
                                    return objectMapper.readValue(json, Player.class);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }).collect(Collectors.toList()));
                });
    }

    private Mono<List<Player>> fallbackFetchFromMongo(String regionId, int count) {
        return playerRepository.findByRegionIdAndStatus(regionId, "available")
                .take(count)
                .collectList();
    }
}
