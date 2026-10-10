package com.example.codebility_api.dto;

public record LoginResponse(
        String message,
        String accessToken,
        String tokenType,
        long expiresIn,
        AccountDetails account
) {
    public record AccountDetails(
            String id,
            String email,
            String firstName,
            String lastName,
            String gender,
            String role,
            String status,
            boolean verified
    ) {
    }
}