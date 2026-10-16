package com.chatop.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.chatop.backend.entity.Rental;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RentalDetailResponse(Integer id, String name, BigDecimal surface, BigDecimal price,
        List<String> picture, String description, @JsonProperty("owner_id") Integer ownerId,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt) {
    public static RentalDetailResponse from(Rental rental) {
        List<String> pictures = rental.getPicture() == null ? List.of() : List.of(rental.getPicture());
        return new RentalDetailResponse(rental.getId(), rental.getName(), rental.getSurface(), rental.getPrice(),
                pictures, rental.getDescription(), rental.getOwnerId(), rental.getCreatedAt(), rental.getUpdatedAt());
    }
}
