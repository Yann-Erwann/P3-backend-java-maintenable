package com.chatop.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MessageRequest(
        @JsonProperty("rental_id") @NotNull @Positive Integer rentalId,
        @JsonProperty("user_id") @NotNull @Positive Integer userId,
        @NotBlank @Size(max = 2000) String message) {
    public MessageRequest {
        message = message == null ? null : message.strip();
    }
}
