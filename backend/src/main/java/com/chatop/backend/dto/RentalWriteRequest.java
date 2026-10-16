package com.chatop.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RentalWriteRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal surface,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Size(max = 2000) String description) {
    public RentalWriteRequest {
        name = name == null ? null : name.strip();
        description = description == null ? null : description.strip();
    }
}
