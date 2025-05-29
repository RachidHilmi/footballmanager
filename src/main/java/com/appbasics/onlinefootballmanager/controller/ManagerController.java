package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.dto.TeamSelectionRequest;
import com.appbasics.onlinefootballmanager.model.Manager;
import com.appbasics.onlinefootballmanager.model.Slot;
import com.appbasics.onlinefootballmanager.model.Trophy;
import com.appbasics.onlinefootballmanager.repository.firestore.ManagerRepository;
import com.appbasics.onlinefootballmanager.service.ManagerService;
import com.appbasics.onlinefootballmanager.util.RegisterResponse;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    @Autowired
    private  ManagerService managerService;
    @Autowired
    private ManagerRepository managerRepository;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody Manager manager) {
        try {
            RegisterResponse response = managerService.registerManager(manager).block();
            return ResponseEntity.ok(response); // Returning managerId
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> credentials) {
        try {
            String token = managerService.login(credentials.get("name"), credentials.get("password")).block();
            return ResponseEntity.ok(Collections.singletonMap("token", token));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @GetMapping("/{managerId}/trophies")
    public Mono<ResponseEntity<List<Trophy>>> getTrophies(@PathVariable String managerId) {
        return managerService.getManagerTrophies(managerId)
                .map(trophies -> ResponseEntity.ok().body(trophies))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

}
