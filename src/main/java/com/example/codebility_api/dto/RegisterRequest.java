package com.example.codebility_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotBlank
        String gender,

        @Size(max = 30)
        String phone,

        @Pattern(
                regexp = "Finance|HR|Engineering|Administration|Operation|Marketing",
                message = "Invalid department"
        )
        String department,

        @NotNull
        @AssertTrue(message = "Terms must be accepted")
        Boolean acceptTerms

) {}

