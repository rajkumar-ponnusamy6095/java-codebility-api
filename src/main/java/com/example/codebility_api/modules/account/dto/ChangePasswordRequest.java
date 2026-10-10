package com.example.codebility_api.modules.account.dto;

import com.example.codebility_api.shared.validation.FieldsMatch;
import jakarta.validation.constraints.*;

@FieldsMatch(field = "newPassword", confirmField = "confirmPassword")
public record ChangePasswordRequest(
        @NotBlank(message = "Old password is required")
        String oldPassword,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters")
        String newPassword,
        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {}
