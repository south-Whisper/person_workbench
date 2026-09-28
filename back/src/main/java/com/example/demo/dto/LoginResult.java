package com.example.demo.dto;

import lombok.Data;

@Data
public class LoginResult {
    private String token;
    private String username;
    private String role;
}
