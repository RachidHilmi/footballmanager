package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.TransferRecord;
import com.appbasics.onlinefootballmanager.model.Player;
import com.appbasics.onlinefootballmanager.model.Slot;
import com.appbasics.onlinefootballmanager.model.TransferListing;
import com.appbasics.onlinefootballmanager.repository.firestore.ManagerRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.PlayerRepository;
import com.appbasics.onlinefootballmanager.util.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

@Service
public class TransferMarketService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SystemCandidateService systemCandidateService;
    @Autowired
    private PlayerCacheService playerCacheService;
    @Autowired
    private TransferHistoryService transferHistoryService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private ManagerRepository managerRepository;
    @Autowired
    private LeagueInstanceRepository leagueInstanceRepository;

    private static final Duration LISTINGS_TTL = Duration.ofMinutes(30);
    private static final int MAX_LISTINGS_PER_SLOT = 4;

    private String getTransferKey(String regionId, String instanceId) {
        return "transfer:listings:" + regionId + ":" + instanceId;
    }

    private String getManagerSlotKey(String managerId, String slotId) {
        return "transfer:manager_slot:" + managerId + ":" + slotId;
    }

    public Mono<Void> listPlayerForTransfer(String regionId, String instanceId, String teamId, String managerId,
                                            String slotId, String playerId, int playerAge,double askingPrice, double playerMarketValue) {
        String transferKey = getTransferKey(regionId, instanceId);
        String managerSlotKey = getManagerSlotKey(managerId, slotId);

        return RedisUtils.getSize(redisTemplate, managerSlotKey)
                .flatMap(count -> {
                    if (count >= MAX_LISTINGS_PER_SLOT) {
                        return Mono.error(new RuntimeException("Maximum 4 listings per slot"));
                    }
                    TransferListing listing = new TransferListing(
                            UUID.randomUUID().toString(),
                            playerId,
                            playerAge,
                            teamId,
                            managerId,
                            regionId,
                            instanceId,
                            slotId,
                            askingPrice,
                            playerMarketValue,
                            System.currentTimeMillis(),
                            false,
                            null,
                            null,
                            null,
                            false,
                            null,
                            null,
                            null
                    );
                    try {
                        String json = objectMapper.writeValueAsString(listing);
                        redisTemplate.opsForList().rightPush(transferKey, json);
                        redisTemplate.opsForList().rightPush(managerSlotKey, listing.getId());
//                        redisTemplate.expire(transferKey, LISTINGS_TTL);
//                        redisTemplate.expire(managerSlotKey, LISTINGS_TTL);

                        String teamPlayersKey = "teamPlayers:" + regionId + ":" + instanceId + ":" + teamId;
                        playerCacheService.deleteCache(teamPlayersKey);
                        return Mono.empty();
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    public Mono<Void> cancelTransferListing(String regionId, String instanceId, String managerId, String slotId, String listingId, String teamId) {
        String transferKey = getTransferKey(regionId, instanceId);
        String managerSlotKey = getManagerSlotKey(managerId, slotId);

        return RedisUtils.getAll(redisTemplate, transferKey)
                .flatMapMany(Flux::fromIterable)
                .index()
                .flatMap(entry -> {
                    long index = entry.getT1();
                    String json = entry.getT2();

                    try {
                        TransferListing listing = objectMapper.readValue(json, TransferListing.class);

                        if (listing.getId().equals(listingId) && !listing.isSold()) {
                            // Listing found -> remove it
                            return Mono.fromRunnable(() -> {
                                redisTemplate.opsForList().set(transferKey, index, "DELETED"); // Mark it
                            }).then();
                        }
                    } catch (Exception e) {
                        // Ignore invalid JSON
                    }
                    return Mono.empty();
                })
                .then(Mono.defer(() -> {
                    redisTemplate.opsForList().remove(transferKey, 0, "DELETED");

                    if (Boolean.TRUE.equals(redisTemplate.hasKey(transferKey))) {
                        Long size = redisTemplate.opsForList().size(transferKey);
                        if (size != null && size == 0) {
                            redisTemplate.delete(transferKey);
                        }
                    }
                    redisTemplate.opsForList().remove(managerSlotKey, 0, listingId);

                    if (Boolean.TRUE.equals(redisTemplate.hasKey(managerSlotKey))) {
                        Long size = redisTemplate.opsForList().size(managerSlotKey);
                        if (size != null && size == 0) {
                            redisTemplate.delete(managerSlotKey);
                        }
                    }

                    clearTeamCache(regionId, instanceId, teamId);

                    return Mono.empty();
                }));
    }

    private void clearTeamCache(String regionId, String instanceId, String teamId) {
        String teamPlayersKey = "teamPlayers:" + regionId + ":" + instanceId + ":" + teamId;
        playerCacheService.deleteCache(teamPlayersKey);
    }

    public Mono<List<TransferListing>> getAvailableListings(String regionId,String instanceId) {

        String transferKey = getTransferKey(regionId, instanceId);

        return RedisUtils.getAll(redisTemplate, transferKey)
                .flatMapMany(Flux::fromIterable)
                .flatMap(json ->  safeDeserialize(json, TransferListing.class))
                .collectList()
                .flatMap(listings -> {
                    long systemCount = listings.stream()
                            .filter(TransferListing::isSystemGenerated)
                            .count();
                    if (systemCount == 0) {
                        return generateSystemListings(regionId, instanceId, 40)
                                .then(Mono.defer(() ->RedisUtils.getAll(redisTemplate, transferKey)
                                                .delaySubscription(Duration.ofMillis(300))
                                                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(100)).take(10)) // retry 10 times
                                                .flatMapMany(Flux::fromIterable)
                                                .flatMap(json -> safeDeserialize(json, TransferListing.class))
                                                .collectList()
                                ));
                    }
                    return Mono.just(listings);
                });
    }

    private <T> Mono<T> safeDeserialize(String json, Class<T> clazz) {
        try {
            return Mono.just(objectMapper.readValue(json, clazz));
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    public Mono<Void> buyPlayerFromMarket(String regionId, String instanceId, String buyerManagerId, String slotId, String listingId) {
        String transferKey = getTransferKey(regionId, instanceId);

        return managerRepository.findByManagerId(buyerManagerId)
                .flatMap(buyerManager -> {
                    Slot buyerSlot = buyerManager.getSlots().get(slotId);
                    if (buyerSlot == null) {
                        return Mono.error(new IllegalArgumentException("Buyer slot not found."));
                    }

                    double buyerBudget;
                    try {
                        buyerBudget = Double.parseDouble(Optional.ofNullable(buyerSlot.getBudget()).orElse("0"));
                    } catch (NumberFormatException e) {
                        return Mono.error(new IllegalStateException("Invalid buyer budget format."));
                    }

                    return RedisUtils.getAll(redisTemplate, transferKey)
                            .flatMapMany(Flux::fromIterable)
                            .flatMap(json -> {
                                try {
                                    TransferListing listing = objectMapper.readValue(json, TransferListing.class);
                                    if (!listing.getId().equals(listingId) || listing.isSold()) {
                                        return Mono.empty();
                                    }

                                    if (buyerManagerId.equals(listing.getManagerId()) ||
                                            buyerSlot.getTeamId().equals(listing.getTeamId())) {
                                        return Mono.error(new IllegalStateException("You cannot buy your own listed player!"));
                                    }

                                    double price = listing.getAskingPrice();
                                    if (buyerBudget < price) {
                                        return Mono.error(new IllegalStateException("Insufficient budget to buy this player."));
                                    }

                                    // 🔧 Fix: Set buyer info before generating ID
                                    listing.setBuyerTeamId(buyerSlot.getTeamId());
                                    listing.setBuyerLeagueId(instanceId);

                                    // Seller budget update if seller is human
                                    Mono<Void> updateSellerBudget;
                                    if (listing.getManagerId() != null && !listing.isSystemGenerated()) {
                                        updateSellerBudget = managerRepository.findByManagerId(listing.getManagerId())
                                                .flatMap(sellerManager -> {
                                                    Optional<Slot> sellerSlotOpt = sellerManager.getSlots().values().stream()
                                                            .filter(s -> listing.getTeamId().equals(s.getTeamId()))
                                                            .findFirst();

                                                    if (sellerSlotOpt.isPresent()) {
                                                        Slot sellerSlot = sellerSlotOpt.get();
                                                        double current = Double.parseDouble(Optional.ofNullable(sellerSlot.getBudget()).orElse("0"));
                                                        sellerSlot.setBudget(String.format("%.2f", current + price));
                                                        return managerRepository.save(sellerManager).then();
                                                    }
                                                    return Mono.empty(); // AI or no slot
                                                });
                                    } else {
                                        updateSellerBudget = Mono.empty();
                                    }

                                    return playerRepository.findByPlayerId(listing.getPlayerId())
                                            .flatMap(player -> {
                                                String oldTeamId = player.getTeamId();
                                                String oldLeagueId = player.getLeagueId();
                                                String oldInstanceId = player.getInstanceId();

                                                return generateUniquePlayerId(instanceId, buyerSlot.getTeamId())
                                                        .flatMap(newPlayerId -> {
                                                            if (player.getOriginalTeamId() == null || player.getOriginalTeamId().isEmpty()) {
                                                                player.setOriginalTeamId(oldTeamId); // store seller team as the original
                                                            }

                                                            player.setPlayerId(newPlayerId);
                                                            player.setTeamId(buyerSlot.getTeamId());
                                                            player.setLeagueId(buyerSlot.getLeagueId());
                                                            player.setInstanceId(buyerSlot.getInstanceId());

                                                            listing.setSold(true);
                                                            listing.setBuyerId(buyerManagerId);

                                                            double remaining = buyerBudget - price;
                                                            buyerSlot.setBudget(String.format("%.2f", remaining));

                                                            TransferRecord record = new TransferRecord(
                                                                    UUID.randomUUID().toString(),
                                                                    newPlayerId,
                                                                    listing.getManagerId(),     // oldManagerId
                                                                    buyerManagerId,             // newManagerId
                                                                    oldTeamId,
                                                                    buyerSlot.getTeamId(),
                                                                    buyerSlot.getLeagueId(),                 // newLeagueId
                                                                    oldLeagueId,
                                                                    instanceId,
                                                                    regionId,
                                                                    price,
                                                                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()),
                                                                    listing.isSystemGenerated()
                                                            );

                                                            return playerRepository.save(player)
                                                                    .then(Mono.fromRunnable(() -> {
                                                                        String teamPlayersKey = "teamPlayers:" + regionId + ":" + instanceId + ":" + buyerSlot.getTeamId();
                                                                        playerCacheService.deleteCache(teamPlayersKey);
                                                                    }))
                                                                    .then(RedisUtils.removeFromList(redisTemplate, transferKey, listingId))
                                                                    .then(managerRepository.save(buyerManager))
                                                                    .then(updateSellerBudget)
                                                                    .then(
                                                                            leagueInstanceRepository.findFirstByInstanceId(instanceId)
                                                                                    .flatMap(instance -> {
                                                                                        if (instance.getTransfers() == null) {
                                                                                            instance.setTransfers(new ArrayList<>());
                                                                                        }

                                                                                        if (instance.getTransfers().size() >= 100) {
                                                                                            instance.getTransfers().remove(0); // oldest
                                                                                        }

                                                                                        instance.getTransfers().add(record);
                                                                                        return leagueInstanceRepository.save(instance);
                                                                                    })
                                                                    )
                                                                    .then(transferHistoryService.saveTransferRecord(record));
                                                        });
                                            });

                                } catch (Exception e) {
                                    return Mono.empty();
                                }
                            }).then();
                });
    }

    public Mono<Void> simulateAIBuyers(String regionId, String instanceId) {
        String transferKey = getTransferKey(regionId, instanceId);

        return RedisUtils.getAll(redisTemplate, transferKey)
                .flatMapMany(Flux::fromIterable)
                .index()
                .collectList()
                .flatMap(indexedList -> {
                    List<Mono<Void>> actions = new ArrayList<>();

                    for (Tuple2<Long, String> entry : indexedList) {
                        long index = entry.getT1();
                        String json = entry.getT2();

                        try {
                            TransferListing listing = objectMapper.readValue(json, TransferListing.class);
                            if (listing.isSold()) continue;

                            if (listing.isSystemGenerated()) {
                                double newPrice = listing.getAskingPrice() * 0.97;
                                listing.setAskingPrice(Math.round(newPrice * 100.0) / 100.0);

                                if (listing.getAskingPrice() <= listing.getPlayerMarketValue() * 1.1) {
                                    actions.add(Mono.fromRunnable(() ->
                                            redisTemplate.opsForList().set(transferKey, index, "EXPIRED")
                                    ).subscribeOn(Schedulers.boundedElastic()).then());
                                    continue;
                                }

                                String updatedJson = objectMapper.writeValueAsString(listing);
                                actions.add(Mono.fromRunnable(() ->
                                        redisTemplate.opsForList().set(transferKey, index, updatedJson)
                                ).subscribeOn(Schedulers.boundedElastic()).then());

                            } else {
                                double chanceToBuy = calculateBuyingChance(
                                        listing.getPlayerAge(),
                                        listing.getPlayerMarketValue(),
                                        listing.getAskingPrice()
                                );

                                if (Math.random() < chanceToBuy) {
                                    listing.setSold(true);
                                    listing.setBuyerId("AI_BUYER");

                                    Mono<Void> process = playerRepository.findByPlayerId(listing.getPlayerId())
                                            .flatMap(originalPlayer -> {
                                                String oldTeamId = originalPlayer.getTeamId();
                                                String oldLeagueId = originalPlayer.getLeagueId();

                                                originalPlayer.setTeamId(null); // remove from seller

                                                Mono<Void> updateSellerBudget = Mono.empty();
                                                if (listing.getManagerId() != null) {
                                                    updateSellerBudget = managerRepository.findByManagerId(listing.getManagerId())
                                                            .flatMap(sellerManager -> {
                                                                Optional<Slot> maybeSlot = sellerManager.getSlots().values().stream()
                                                                        .filter(s -> listing.getTeamId().equals(s.getTeamId()))
                                                                        .findFirst();
                                                                if (maybeSlot.isPresent()) {
                                                                    Slot sellerSlot = maybeSlot.get();
                                                                    double current = Double.parseDouble(Optional.ofNullable(sellerSlot.getBudget()).orElse("0"));
                                                                    sellerSlot.setBudget(String.format("%.2f", current + listing.getAskingPrice()));
                                                                    return managerRepository.save(sellerManager).then();
                                                                }
                                                                return Mono.empty();
                                                            });
                                                }

                                                TransferRecord record = new TransferRecord(
                                                        UUID.randomUUID().toString(),
                                                        listing.getPlayerId(),
                                                        listing.getManagerId(),
                                                        "AI_BUYER",
                                                        oldTeamId,
                                                        "AI_TEAM",
                                                        listing.getBuyerLeagueId(),
                                                        listing.getInstanceId(),
                                                        oldLeagueId,
                                                        listing.getRegionId(),
                                                        listing.getAskingPrice(),
                                                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()),
                                                        listing.isSystemGenerated()
                                                );

                                                return playerRepository.save(originalPlayer)
                                                        .then(updateSellerBudget)
                                                        .then(transferHistoryService.saveTransferRecord(record))
                                                        .then(
                                                                leagueInstanceRepository.findFirstByInstanceId(instanceId)
                                                                        .flatMap(instance -> {
                                                                            if (instance.getTransfers() == null) {
                                                                                instance.setTransfers(new ArrayList<>());
                                                                            }

                                                                            if (instance.getTransfers().size() >= 100) {
                                                                                instance.getTransfers().remove(0);
                                                                            }

                                                                            instance.getTransfers().add(record);
                                                                            return leagueInstanceRepository.save(instance);
                                                                        })
                                                        )
                                                        .then(Mono.fromRunnable(() -> {
                                                            try {
                                                                redisTemplate.opsForList().set(transferKey, index, objectMapper.writeValueAsString(listing));
                                                            } catch (Exception e) {
                                                                throw new RuntimeException(e);
                                                            }
                                                        }));
                                            }).subscribeOn(Schedulers.boundedElastic()).then();

                                    actions.add(process);
                                }
                            }
                        } catch (Exception e) {
                            // Ignore corrupted entries
                        }
                    }

                    return Flux.merge(actions)
                            .then(Mono.fromRunnable(() ->
                                    redisTemplate.opsForList().remove(transferKey, 0, "EXPIRED")
                            ).subscribeOn(Schedulers.boundedElastic()))
                            .then();
                });
    }

    private Mono<String> generateUniquePlayerId(String instanceId, String teamId) {
        return playerRepository.findByInstanceIdAndTeamId(instanceId, teamId)
                .collectList()
                .map(players -> {
                    int maxSuffix = players.stream()
                            .map(Player::getPlayerId)
                            .map(id -> {
                                String[] parts = id.split("_");
                                try {
                                    return Integer.parseInt(parts[parts.length - 1]);
                                } catch (NumberFormatException e) {
                                    return 0;
                                }
                            }).max(Integer::compareTo).orElse(0);

                    return instanceId + "_" + teamId + "_player_" + (maxSuffix + 1);
                });
    }


    private double calculateBuyingChance(int age, double marketValue, double askingPrice) {
        double priceRatio = askingPrice / marketValue;
        double baseChance;

        if (priceRatio <= 1.2) {
            // Asking price is close to market value (good deal)
            baseChance = 0.6;
        } else if (priceRatio <= 2.0) {
            // Asking price is up to 2x market value (acceptable)
            baseChance = 0.4;
        } else {
            // Asking price more than double market value (too expensive)
            baseChance = 0.2;
        }

        if (age <= 21) {
            baseChance *= 1.4;
        } else if (age <= 25) {
            baseChance *= 1.2;
        } else if (age <= 29) {
            baseChance *= 1.0;
        } else if (age <= 33) {
            baseChance *= 0.8;
        } else {
            baseChance *= 0.6;
        }

        return Math.max(0.05, Math.min(baseChance, 0.95));
    }

    private double calculateAskingPriceMultiplier(int age) {
        if (age <= 21) return 3.0;
        if (age <= 25) return 2.8;
        if (age <= 29) return 2.5;
        if (age <= 33) return 2.2;
        return 2.0;
    }

    public Mono<Void> generateSystemListings(String regionId, String instanceId, int targetCount) {
        String transferKey = getTransferKey(regionId, instanceId);
        System.out.println("🔍 transferKey from generate = " + transferKey);
        return RedisUtils.getAll(redisTemplate, transferKey)
                .flatMapMany(Flux::fromIterable)
                .flatMap(json -> {
                    try {
                        TransferListing listing = objectMapper.readValue(json, TransferListing.class);
                        return Mono.just(listing);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .collectList()
                .flatMap(currentListings -> {
                    long systemCount = currentListings.stream()
                            .filter(TransferListing::isSystemGenerated)
                            .count();

                    int toGenerate = targetCount - (int) systemCount;
                    if (toGenerate <= 0) return Mono.empty();

                    int avgAttack = 60, avgDefense = 60, avgOverall = 60;

                    List<TransferListing> humanListings = currentListings.stream()
                            .filter(listing -> !listing.isSystemGenerated())
                            .toList();

                    if (!humanListings.isEmpty()) {
                        avgAttack = (int) humanListings.stream().mapToDouble(TransferListing::getPlayerAge).average().orElse(60);
                        avgDefense = avgAttack;
                        avgOverall = avgAttack;
                    }

                    String excludedLeagueId = extractLeagueIdFromInstanceId(instanceId);

                    return systemCandidateService.getBoostedSystemCandidates(regionId, toGenerate * 2, avgAttack, avgDefense, avgOverall)
                            .flatMapMany(Flux::fromIterable)
                            .filter(p -> p.getLeagueId() == null || !p.getLeagueId().equals(excludedLeagueId)) // exclude players from same league
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
                                        forwards.add(p);
                                    } else if (pos.contains("midfield") || pos.equals("cm") || pos.equals("lm") || pos.equals("rm")) {
                                        midfielders.add(p);
                                    } else if (pos.contains("defender") || pos.equals("cb") || pos.equals("lb") || pos.equals("rb")) {
                                        defenders.add(p);
                                    } else if (pos.contains("goalkeeper") || pos.equals("gk") || pos.contains("keeper")) {
                                        goalkeepers.add(p);
                                    }
                                }

                                System.out.println("[DEBUG] Total filtered players: " + players.size());
                                System.out.println("✔ forwards: " + forwards.size());
                                System.out.println("✔ midfielders: " + midfielders.size());
                                System.out.println("✔ defenders: " + defenders.size());
                                System.out.println("✔ goalkeepers: " + goalkeepers.size());

                                List<Player> selectedPlayers = new ArrayList<>();
                                int perPosition = targetCount / 4;

                                selectedPlayers.addAll(pickRandom(forwards, perPosition));
                                selectedPlayers.addAll(pickRandom(midfielders, perPosition));
                                selectedPlayers.addAll(pickRandom(defenders, perPosition));
                                selectedPlayers.addAll(pickRandom(goalkeepers, perPosition));

                                int remaining = targetCount - selectedPlayers.size();
                                if (remaining > 0) {
                                    List<Player> fallbackPool = new ArrayList<>();
                                    fallbackPool.addAll(forwards);
                                    fallbackPool.addAll(midfielders);
                                    fallbackPool.addAll(defenders);
                                    fallbackPool.addAll(goalkeepers);
                                    fallbackPool.removeAll(selectedPlayers);
                                    Collections.shuffle(fallbackPool);
                                    selectedPlayers.addAll(fallbackPool.subList(0, Math.min(remaining, fallbackPool.size())));
                                }

                                return Flux.fromIterable(selectedPlayers)
                                        .flatMap(player -> createSystemListing(player, regionId, instanceId, transferKey))
                                        .collectList()
                                        .then();
                            });


                });
    }

    public static String extractLeagueIdFromInstanceId(String instanceId) {
        // Example: "Africa_Ghana_01_instance_001" → "Ghana_01"
        String[] parts = instanceId.split("_");
        if (parts.length >= 4) {
            return parts[1] + "_" + parts[2]; // Ghana_01
        }
        return "";
    }

    private List<Player> pickRandom(List<Player> list, int count) {
        Collections.shuffle(list);
        return list.subList(0, Math.min(count, list.size()));
    }

    private Mono<Void> createSystemListing(Player player, String regionId,String instanceId, String transferKey) {
        double marketValue = parseMarketValue(player.getCurrentStats().getMarketValue());
        double multiplier = calculateAskingPriceMultiplier(player.getAge());
        double askingPrice = Math.round(marketValue * multiplier * 100.0) / 100.0;

        TransferListing listing = new TransferListing(
                UUID.randomUUID().toString(),
                player.getPlayerId(),
                player.getAge(),
                player.getTeamId(),
                null,
                regionId,
                instanceId,
                null,
                askingPrice,
                marketValue,
                System.currentTimeMillis(),
                false,
                null,
                null,
                null,
                true,
                player.getName(),
                player.getPosition(),
                player.getNationality()
        );

        try {
            String listingJson = objectMapper.writeValueAsString(listing);
            redisTemplate.opsForList().rightPush(transferKey, listingJson);
            Long redisSize = redisTemplate.opsForList().size(transferKey);
            System.out.println("✅ Redis now has " + redisSize + " listings under " + transferKey);
            redisTemplate.expire(transferKey, LISTINGS_TTL);
            System.out.println("✅ Added system listing: " + listing.getPlayerId());
        } catch (Exception e) {
            System.err.println("❌ Failed to create listing: " + e.getMessage());
            return Mono.empty();
        }
        return Mono.empty();
    }

    public static double parseMarketValue(String value) {
        if (value == null || value.isEmpty()) return 0.0;
        try {
            value = value.replace("$", "").replace("M", "").trim();
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("Invalid market value: " + value);
            return 0.0;
        }
    }


}
