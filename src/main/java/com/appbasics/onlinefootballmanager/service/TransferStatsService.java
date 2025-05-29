package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.TransferRecord;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import org.springframework.beans.factory.annotation.Autowired;
import org.bson.Document;


@Service
public class TransferStatsService {

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    public Flux<TransferRecord> getTopTransfers(String regionId, String leagueId, int topN) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                        org.springframework.data.mongodb.core.query.Criteria.where("regionId").is(regionId)
                                .and("leagueId").is(leagueId)
                ),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "transferPrice"),
                Aggregation.limit(topN)
        );

        // No AggregationResults here — directly Flux
        return mongoTemplate.aggregate(aggregation, "transfer_records", TransferRecord.class);
    }


    public Flux<Document> getTopEarningManagers(String regionId, String leagueId, int topN) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                        org.springframework.data.mongodb.core.query.Criteria.where("regionId").is(regionId)
                                .and("leagueId").is(leagueId)
                ),
                Aggregation.group("sellerManagerId")
                        .sum("transferPrice").as("totalEarnings"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "totalEarnings"),
                Aggregation.limit(topN)
        );

        return mongoTemplate.aggregate(aggregation, "transfer_history", Document.class);
    }

    public Flux<TransferRecord> getTransfersByManager(String managerId) {
        return mongoTemplate.find(
                org.springframework.data.mongodb.core.query.Query.query(
                        new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                                org.springframework.data.mongodb.core.query.Criteria.where("oldManagerId").is(managerId),
                                org.springframework.data.mongodb.core.query.Criteria.where("newManagerId").is(managerId)
                        )
                ),
                TransferRecord.class,
                "transfer_records"
        );
    }

}
