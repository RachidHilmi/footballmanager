package com.appbasics.onlinefootballmanager.config;

import org.springframework.beans.factory.annotation.Value;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Configuration
@Profile("!test")
@EnableReactiveFirestoreRepositories(basePackages = "com.appbasics.onlinefootballmanager.repositories")

public class FirestoreConfig {
    private static final Logger log = LoggerFactory.getLogger(FirestoreConfig.class);


    @Value("${FIREBASE_CONFIG_BASE64:}")
    private String firebaseConfigBase64;

    @ConditionalOnProperty(name = "FIREBASE_CONFIG_BASE64")
//    @Bean
//    public FirebaseApp firebaseApp() throws IOException {
//        if (firebaseConfigBase64 == null || firebaseConfigBase64.isEmpty()) {
//            throw new IllegalStateException("Missing FIREBASE_CONFIG_BASE64 environment variable");
//        }
//        byte[] decoded = Base64.getDecoder().decode(firebaseConfigBase64);
//        GoogleCredentials credentials = GoogleCredentials.fromStream(
//                new ByteArrayInputStream(decoded)
//        );
//
//        FirebaseOptions options = FirebaseOptions.builder()
//                .setCredentials(credentials)
//                .build();
//
//        return FirebaseApp.initializeApp(options);
//    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            log.warn("Firestore bean not created because FirebaseApp is null.");
            return null;
        }
        return FirestoreClient.getFirestore(firebaseApp);
    }
}