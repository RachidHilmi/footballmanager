//package com.appbasics.onlinefootballmanager.service;
//
//import com.appbasics.onlinefootballmanager.model.*;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
//import org.springframework.stereotype.Service;
//
//import java.io.InputStream;
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class DataUploadService implements ApplicationRunner {
//
//    private final ReactiveMongoTemplate mongoTemplate;
//    private final ObjectMapper objectMapper;
//
//    @Override
//    public void run(ApplicationArguments args) {
//        System.out.println("📦 Starting DataUploadService...");
//        //upload("players.json", new TypeReference<List<Player>>() {}, Player.class, "players");
//        //upload("LeagueInstances.json", new TypeReference<List<LeagueInstance>>() {}, LeagueInstance.class, "LeagueInstances");
//        upload("LeagueTemplates.json", new TypeReference<List<LeagueTemplate>>() {}, LeagueTemplate.class, "LeagueTemplates");
//    }
//
//    private <T> void upload(String file, TypeReference<List<T>> typeRef, Class<T> clazz, String collection) {
//        try {
//            InputStream stream = new ClassPathResource(file).getInputStream();
//            List<T> data = objectMapper.readValue(stream, typeRef);
//
//            mongoTemplate.dropCollection(collection)
//                    .then(mongoTemplate.createCollection(collection))
//                    .thenMany(mongoTemplate.insertAll(data))
//                    .doOnSubscribe(s -> System.out.println("📤 Uploading " + data.size() + " docs to collection: " + collection))
//                    .doOnNext(doc -> System.out.println("➡ Inserted: " + doc))
//                    .doOnError(e -> System.err.println("❌ Failed to upload " + collection + ": " + e.getMessage()))
//                    .doOnComplete(() -> System.out.println("✅ Upload complete for: " + collection))
//                    .subscribe();
//        } catch (Exception e) {
//            System.err.println("❌ Error reading " + file + ": " + e.getMessage());
//        }
//    }
//}
