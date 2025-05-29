package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.repository.firestore.LeagueRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.TransferOfferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransferMarketScheduler {

    @Autowired
    private TransferMarketService transferMarketService;
    @Autowired
    private TransferOfferService transferOfferService;
    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private TransferOfferRepository transferOfferRepository;

//    @Scheduled(cron = "0 0 */6 * * *") // Every 6 hours
//    public void refreshAllMarkets() {
//        List<String> regions = List.of("Africa", "Asia", "Europe", "Americas");
//
//        for (String region : regions) {
//            leagueRepository.findByRegionIdAndStatus(region, "active")
//                    .flatMap(league ->
//                            transferMarketService.simulateAIBuyers(region, league.getLeagueId())
//                                    .then(transferMarketService.generateSystemListings(region, league.getLeagueId(), 10))
//                    )
//                    .subscribe();
//        }
//    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void expireOldOffers() {
        long sixteenHoursAgo = System.currentTimeMillis() - (16 * 60 * 60 * 1000L);

        transferOfferRepository.findByAcceptedFalseAndRejectedFalseAndOfferedAtLessThan(sixteenHoursAgo)
                .flatMap(offer -> transferOfferService.rejectOffer(offer.getOfferId()))
                .subscribe();
    }
}
