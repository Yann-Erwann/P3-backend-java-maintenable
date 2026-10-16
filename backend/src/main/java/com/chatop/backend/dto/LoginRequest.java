package com.chatop.backend.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
