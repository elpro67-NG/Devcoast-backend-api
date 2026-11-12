package com.example.devcoast.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/v1")
public class MobileApiController {

    @GetMapping("/test")
    public Map<String, String> test() {
        return Map.of(
            "estado", "OK",
            "mensaje", "API móvil de DevCoast funcionando"
        );
    }
}