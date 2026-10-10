
package com.example.codebility_api.modules.account.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidateResetTokenRequest(
        @NotBlank(message = "Reset token is required")
        String token
) {
}
