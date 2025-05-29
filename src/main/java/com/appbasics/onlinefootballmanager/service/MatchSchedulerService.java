package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class MatchSchedulerService {

    private final LeagueInstanceRepository leagueInstanceRepository;
    private final MatchRepository matchRepository;

    private final MatchService matchService;

    @Scheduled(cron = "0 0 20 * * *") // Every day at 20:00
    public void autoSimulateMatchdays() {
        ZoneId zoneId = ZoneId.of("UTC");

        LocalDateTime startOfToday = LocalDate.now(zoneId).atStartOfDay();
        LocalDateTime endOfToday = startOfToday.plusDays(1).minusNanos(1);

        Date from = Date.from(startOfToday.atZone(zoneId).toInstant());
        Date to = Date.from(endOfToday.atZone(zoneId).toInstant());

        System.out.println("Checking window: " + from + " → " + to);
        leagueInstanceRepository.findAll()
                .filter(instance -> "active".equalsIgnoreCase(instance.getStatus()))
                .flatMap(instance ->
                        matchRepository.findAllByInstanceId(instance.getInstanceId())
                                .filter(match -> {
                                    Date matchDate = match.getMatchDate();
                                    System.out.println("Match date: " + matchDate);

                                    return matchDate != null && !matchDate.before(from) && !matchDate.after(to);
                                })
                                .hasElements()
                                .flatMap(hasTodayMatches -> {
                                    if (hasTodayMatches) {
                                        return matchService.finalizeMatchday(instance.getInstanceId(), instance.getCurrentMatchday());
                                    }
                                    return Mono.empty(); // Skip if no matches today
                                })
                                .onErrorResume(e -> {
                                    System.err.println("❌ Error finalizing matchday for " + instance.getInstanceId() + ": " + e.getMessage());
                                    return Mono.empty();
                                })
                )
                .subscribe();
    }
}

