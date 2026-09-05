package com.pablo.jobflow.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {
    @GetMapping("/api/version")
    public Message version() {
        return new Message(
            "JobFlow",
            "1.0.0",
            "Spring Boot"
        );
    }

    public record Message(
        String name,
        String version,
        String framework
    ) {}
}