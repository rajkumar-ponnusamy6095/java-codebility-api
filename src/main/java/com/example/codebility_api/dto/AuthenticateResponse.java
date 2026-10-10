package com.example.codebility_api.dto;

public record AuthenticateResponse(
        String email,
        String jwtToken
) {}
