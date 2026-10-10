package com.example.codebility_api.modules.account.dto;

import com.example.codebility_api.shared.validation.FieldsMatch;
import jakarta.validation.constraints.*;

/**
 * Partial update: null or empty fields are left unchanged. Gender may be
 * omitted but not blanked out.
 */
@FieldsMatch(field = "password", confirmField = "confirmPassword")
public record UpdateAccountRequest(
        @Email(message = "Invalid email format") @Size(max = 254)
        String email,
        @Size(max = 100)
        String firstName,
        @Size(max = 100)
        String lastName,
        @Pattern(regexp = "(?s).*\\S.*", message = "Gender must not be blank") @Size(max = 50)
        String gender,
        @Size(max = 30)
        String phone,
        @Pattern(regexp = "^$|Finance|HR|Engineering|Administration|Operation|Marketing", message = "Invalid department")
        String department,
        @Pattern(regexp = "^$|Admin|User", message = "Role must be Admin or User")
        String role,
        @Pattern(regexp = "^$|active|inactive", message = "Status must be active or inactive")
        String status,
        @Pattern(regexp = "(?s)^$|.{8,72}", message = "Password must be between 8 and 72 characters")
        String password,
        String confirmPassword
) {}
