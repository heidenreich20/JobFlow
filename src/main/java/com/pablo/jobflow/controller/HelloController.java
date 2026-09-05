package com.pablo.jobflow.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Message hello() {
        return new Message("Hello from JobFlow!");
    }

    public record Message(String message) {}
}