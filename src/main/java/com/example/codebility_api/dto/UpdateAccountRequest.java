package com.example.codebility_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email")
        String email,

        @NotBlank(message = "First name is required")
        @Size(max = 50)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50)
        String lastName,

        @NotBlank(message = "Gender is required")
        @Size(max = 50)
        String gender,

        String phone,

        String department,

        @Pattern(
                regexp = "Admin|User",
                message = "Role must be Admin or User"
        )
        String role,

        @Pattern(
                regexp = "active|inactive",
                message = "Status must be active or inactive"
        )
        String status
) {}
