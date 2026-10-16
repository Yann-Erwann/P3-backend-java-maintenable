package com.chatop.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.chatop.backend.entity.Rental;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RentalSummaryResponse(Integer id, String name, BigDecimal surface, BigDecimal price,
        String picture, String description, @JsonProperty("owner_id") Integer ownerId,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt) {
    public static RentalSummaryResponse from(Rental rental) {
        return new RentalSummaryResponse(rental.getId(), rental.getName(), rental.getSurface(), rental.getPrice(),
                rental.getPicture(), rental.getDescription(), rental.getOwnerId(),
                rental.getCreatedAt(), rental.getUpdatedAt());
    }
}
