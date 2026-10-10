package com.example.codebility_api.modules.account.dto;

import jakarta.validation.constraints.*;

/** Admin-created account; the user sets a password through the emailed setup link. */
public record CreateAccountRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") @Size(max = 254)
        String email,
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
        @NotBlank(message = "Role is required")
        @Pattern(regexp = "Admin|User", message = "Role must be Admin or User")
        String role,
        @Pattern(regexp = "active|inactive", message = "Status must be active or inactive")
        String status
) {}
