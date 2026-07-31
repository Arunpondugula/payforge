package com.payforge.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank(message = "firstName is required")
        String firstName,

        @NotBlank(message = "lastName is required")
        String lastName,

        @NotBlank(message = "contactEmail is required")
        @Email(message = "contactEmail must be a valid email address")
        String contactEmail,

        @Positive(message = "initialBalance must be positive")
        BigDecimal initialBalance,

        @NotBlank(message = "currency is required")
        String currency
) {}