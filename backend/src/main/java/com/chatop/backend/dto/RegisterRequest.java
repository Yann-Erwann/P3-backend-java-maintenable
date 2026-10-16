package com.chatop.backend.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(min = 8, max = 72) String password) {
    public RegisterRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        name = name == null ? null : name.strip();
    }
}
