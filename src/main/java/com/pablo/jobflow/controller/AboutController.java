package com.pablo.jobflow.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AboutController {

    @GetMapping("/api/about")
    public Message about() {
        return new Message(
            "Heiden",
            "Spring Boot",
            "JobFlow"
        );
    }

    public record Message(
        String name,
        String learning,
        String goal
    ) {}
}