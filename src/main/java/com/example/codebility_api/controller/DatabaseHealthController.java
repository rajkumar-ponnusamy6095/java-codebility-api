package com.example.codebility_api.controller;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DatabaseHealthController {

    private final MongoTemplate mongoTemplate;

    public DatabaseHealthController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/api/health/database")
    public Map<String, String> databaseHealth() {
        mongoTemplate.getDb().runCommand(
                new Document("ping", 1)
        );

        return Map.of(
                "status", "UP",
                "database", mongoTemplate.getDb().getName()
        );
    }
}