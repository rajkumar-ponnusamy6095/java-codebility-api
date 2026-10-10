package com.example.codebility_api.modules.account.dto;

public record AuthenticationResult(String email, String jwtToken, String refreshToken) {

    public AuthenticateResponse toResponse() {
        return new AuthenticateResponse(email, jwtToken);
    }
}
