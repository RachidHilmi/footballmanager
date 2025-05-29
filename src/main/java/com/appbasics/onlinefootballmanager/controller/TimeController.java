package com.appbasics.onlinefootballmanager.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;

@RestController
@RequestMapping("/api/time")
public class TimeController {

    @GetMapping("/now")
    public ResponseEntity<Long> getCurrentServerTime() {
        return ResponseEntity.ok(System.currentTimeMillis());
    }

    @GetMapping("/zone")
    public ResponseEntity<String> getServerTimeZone() {
        return ResponseEntity.ok(ZoneId.systemDefault().toString());
    }
}
