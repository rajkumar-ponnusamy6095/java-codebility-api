package com.example.codebility_api.modules.account.dto;

import java.time.Instant;

public record AccountProfileResponse(
        String id,
        String gender,
        String firstName,
        String lastName,
        String email,
        String phone,
        String department,
        String status,
        String role,
        Instant createdAt,
        Instant updated,
        boolean isVerified
) {}
