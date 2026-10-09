package com.ghana.dashboard.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        String now = OffsetDateTime.now().toString();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "ghana-dashboard-api");
        data.put("timestamp", now);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "OK");
        body.put("data", data);
        body.put("timestamp", now);
        return ResponseEntity.ok(body);
    }
}