package com.chatop.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.chatop.backend.entity.Rental;

class MessageTests extends ApiTestSupport {
    @Test
    void messagesArePersistedWithTheAuthenticatedAuthorAndRejectImpersonation() throws Exception {
        String ownerJwt = token(register(email()));
        int ownerId = Integer.parseInt(decoder.decode(ownerJwt).getSubject());
        Rental rental = rentals.saveAndFlush(new Rental("Message house", BigDecimal.ONE, BigDecimal.TEN,
                "https://example.test/image.png", "Description", ownerId));
        try {
            String senderJwt = token(register(email()));
            String senderId = decoder.decode(senderJwt).getSubject();
            var response = post("/api/messages", Map.of("rental_id", rental.getId().toString(),
                    "user_id", senderId, "message", "  Is the house available?  "), senderJwt);
            assertEquals(200, response.statusCode());
            var stored = messages.findAll().getFirst();
            assertEquals(Integer.valueOf(senderId), stored.getUserId());
            assertEquals(rental.getId(), stored.getRentalId());
            assertEquals("Is the house available?", stored.getMessage());
            assertNotNull(stored.getCreatedAt());
            long count = messages.count();
            assertEquals(403, post("/api/messages", Map.of("rental_id", rental.getId().toString(),
                    "user_id", Integer.toString(ownerId), "message", "Impersonation"), senderJwt).statusCode());
            assertEquals(404, post("/api/messages", Map.of("rental_id", "2147483647",
                    "user_id", senderId, "message", "Missing rental"), senderJwt).statusCode());
            for (String content : java.util.List.of(" ", "x".repeat(2001))) {
                assertEquals(400, post("/api/messages", Map.of("rental_id", rental.getId().toString(),
                        "user_id", senderId, "message", content), senderJwt).statusCode());
            }
            assertEquals(count, messages.count());
        } finally {
            messages.deleteAll();
            rentals.deleteById(rental.getId());
        }
    }

    @Test
    void fractionalMessageIdentifiersAreRejectedWithoutWriting() throws Exception {
        String jwt = token(register(email()));
        int userId = Integer.parseInt(decoder.decode(jwt).getSubject());
        Rental rental = rentals.saveAndFlush(new Rental("Integer identifiers", BigDecimal.ONE, BigDecimal.TEN,
                null, "Description", userId));
        long before = messages.count();
        try {
            for (String field : java.util.List.of("rental_id", "user_id")) {
                Map<String, Object> request = new java.util.HashMap<>(Map.of(
                        "rental_id", rental.getId(), "user_id", userId, "message", "Invalid fractional identifier"));
                int id = field.equals("rental_id") ? rental.getId() : userId;
                request.put(field, new BigDecimal(id + ".75"));
                assertEquals(400, post("/api/messages", request, jwt).statusCode());
                assertEquals(before, messages.count());
            }
        } finally {
            messages.deleteAll();
            rentals.deleteById(rental.getId());
        }
    }
}
