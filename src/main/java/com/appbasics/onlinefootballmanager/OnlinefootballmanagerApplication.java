package com.appbasics.onlinefootballmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication()
@EnableReactiveMongoRepositories(basePackages = "com.appbasics.onlinefootballmanager.repository.mongo")
//@EnableReactiveFirestoreRepositories(basePackages = "com.appbasics.onlinefootballmanager.repository.firestore")


public class OnlinefootballmanagerApplication {

	public static void main(String[] args) {

		SpringApplication.run(OnlinefootballmanagerApplication.class, args);
	}

}
