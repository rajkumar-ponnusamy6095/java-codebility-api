package com.example.codebility_api.modules.account.dto;

public record AuthenticateResponse(
        String email,
        String jwtToken
) {}
