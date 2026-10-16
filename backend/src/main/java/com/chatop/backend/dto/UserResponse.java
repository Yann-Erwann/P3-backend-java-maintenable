package com.chatop.backend.dto;

import java.time.LocalDateTime;

import com.chatop.backend.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserResponse(Integer id, String email, String name,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
