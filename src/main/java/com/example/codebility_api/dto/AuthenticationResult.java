
package com.example.codebility_api.dto;

public record AuthenticationResult(
        LoginResponse response,
        String refreshToken
) {
}
