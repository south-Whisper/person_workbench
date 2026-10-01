package com.example.demo.controller;
import com.example.demo.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/setup/status")
    public Map<String, Object> setupStatus() {
        return auth.setupStatus();
    }

    @PostMapping("/setup")
    public Map<String, Object> setup(@RequestBody Map<String, Object> input) {
        return auth.setup(input);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> input) {
        return auth.login(input);
    }
}
