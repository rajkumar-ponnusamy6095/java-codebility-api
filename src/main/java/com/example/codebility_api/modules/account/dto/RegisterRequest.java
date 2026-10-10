package com.example.codebility_api.modules.account.dto;

import com.example.codebility_api.shared.validation.FieldsMatch;
import jakarta.validation.constraints.*;

@FieldsMatch(field = "password", confirmField = "confirmPassword")
public record RegisterRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") @Size(max = 254)
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,
        @NotBlank(message = "Confirm password is required")
        String confirmPassword,
        @NotBlank(message = "First name is required") @Size(max = 100)
        String firstName,
        @NotBlank(message = "Last name is required") @Size(max = 100)
        String lastName,
        @NotBlank(message = "Gender is required") @Size(max = 50)
        String gender,
        @Size(max = 30)
        String phone,
        @Pattern(regexp = "Finance|HR|Engineering|Administration|Operation|Marketing", message = "Invalid department")
        String department,
        @NotNull @AssertTrue(message = "Terms must be accepted")
        Boolean acceptTerms
) {}
