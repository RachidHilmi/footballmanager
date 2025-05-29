package com.appbasics.onlinefootballmanager.service;

import com.appbasics.onlinefootballmanager.model.TransferRecord;
import com.appbasics.onlinefootballmanager.repository.mongo.TransferRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
public class TransferHistoryService {

    @Autowired
    private TransferRecordRepository transferRecordRepository;

    public Mono<Void> saveTransferRecord(TransferRecord record) {

        return transferRecordRepository.save(record).then();
    }
}
