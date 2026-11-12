package com.example.devcoast.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.Map;

@RestController
public class ApiTestController {

    @CrossOrigin(origins = "http://localhost:51913")
    @GetMapping("/api/test")
    public Map<String, String> test() {
        return Map.of(
            "estado", "OK",
            "mensaje", "Flutter conectado con DevCoast"
        );
    }
}