package com.example.codebility_api.modules.account.dto;

import com.example.codebility_api.shared.validation.FieldsMatch;
import jakarta.validation.constraints.*;

@FieldsMatch(field = "password", confirmField = "confirmPassword")
public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        String token,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,
        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {}
