package com.chatop.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import com.chatop.backend.entity.Rental;
import com.chatop.backend.entity.User;
import tools.jackson.databind.JsonNode;

class RentalTests extends ApiTestSupport {
    @Test
    void rentalListIsAnEmptyArrayWhenThereAreNoRentals() throws Exception {
        assertEquals(0, rentals.count());
        var response = get("/api/rentals", token(register(email())));
        assertEquals(200, response.statusCode());
        assertEquals("{\"rentals\":[]}", response.body());
    }

    @Test
    void rentalListAndDetailReturnStoredValuesWithTheExpectedPictureShapes() throws Exception {
        User owner = users.saveAndFlush(new User(email(), "Rental Owner", passwords.encode(PASSWORD)));
        Rental first = rentals.saveAndFlush(new Rental("First house", new BigDecimal("120"), new BigDecimal("300"),
                "https://example.test/house.jpg", "First description", owner.getId()));
        Rental second = rentals.saveAndFlush(new Rental("Second house", new BigDecimal("75"), new BigDecimal("200"),
                "https://example.test/second.jpg", "Second description", owner.getId()));
        try {
            String jwt = token(register(email()));
            var response = get("/api/rentals", jwt);
            assertEquals(200, response.statusCode());
            JsonNode items = json.readTree(response.body()).get("rentals");
            assertEquals(2, items.size());
            assertEquals(first.getId().intValue(), items.get(0).get("id").asInt());
            assertEquals(second.getId().intValue(), items.get(1).get("id").asInt());
            assertEquals("First house", items.get(0).get("name").asString());
            assertEquals("https://example.test/house.jpg", items.get(0).get("picture").asString());
            assertEquals(owner.getId().intValue(), items.get(0).get("owner_id").asInt());
            assertFalse(items.get(0).has("owner"));
            assertFalse(response.body().contains("password"));

            var detail = get("/api/rentals/" + first.getId(), jwt);
            assertEquals(200, detail.statusCode());
            JsonNode body = json.readTree(detail.body());
            assertEquals("First house", body.get("name").asString());
            assertEquals(120, body.get("surface").asInt());
            assertEquals(300, body.get("price").asInt());
            assertEquals("First description", body.get("description").asString());
            assertTrue(body.get("picture").isArray());
            assertEquals(1, body.get("picture").size());
            assertEquals("https://example.test/house.jpg", body.get("picture").get(0).asString());
            assertEquals(owner.getId().intValue(), body.get("owner_id").asInt());
            assertFalse(body.get("created_at").isNull());
            assertFalse(body.get("updated_at").isNull());
        } finally {
            rentals.deleteAllById(java.util.List.of(first.getId(), second.getId()));
        }
    }

    @Test
    void authenticatedUsersCanReadAnotherUsersPublicProfileWithoutThePassword() throws Exception {
        User owner = users.saveAndFlush(new User(email(), "Public Owner", passwords.encode(PASSWORD)));
        String jwt = token(register(email()));
        assertNotEquals(owner.getId().toString(), decoder.decode(jwt).getSubject());
        var response = get("/api/user/" + owner.getId(), jwt);
        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        assertEquals(owner.getId().intValue(), body.get("id").asInt());
        assertEquals("Public Owner", body.get("name").asString());
        assertEquals(owner.getEmail(), body.get("email").asString());
        assertTrue(body.has("created_at"));
        assertTrue(body.has("updated_at"));
        assertFalse(body.has("password"));
        assertFalse(response.body().contains(owner.getPassword()));
    }

    @Test
    void missingResourcesReturn404AndInvalidIdsReturn400() throws Exception {
        String jwt = token(register(email()));
        for (String prefix : java.util.List.of("/api/rentals/", "/api/user/")) {
            var missing = get(prefix + Integer.MAX_VALUE, jwt);
            assertEquals(404, missing.statusCode());
            assertTrue(json.readTree(missing.body()).has("message"));
            assertFalse(missing.body().contains("stackTrace"));
            for (String invalid : java.util.List.of("abc", "0", "-1", "2147483648")) {
                assertEquals(400, get(prefix + invalid, jwt).statusCode());
            }
        }
    }

    @Test
    void legacyRentalWithoutPictureReturnsAnEmptyPictureArray() throws Exception {
        User owner = users.saveAndFlush(new User(email(), "Legacy Owner", passwords.encode(PASSWORD)));
        Rental rental = rentals.saveAndFlush(new Rental("Without picture", BigDecimal.ONE, BigDecimal.ONE,
                null, "Legacy description", owner.getId()));
        try {
            var response = get("/api/rentals/" + rental.getId(), token(register(email())));
            assertEquals(200, response.statusCode());
            assertEquals(0, json.readTree(response.body()).get("picture").size());
        } finally {
            rentals.deleteById(rental.getId());
        }
    }

    @Test
    void createAndUpdatePersistDecimalsAndPreserveTheOwnerAndPicture() throws Exception {
        String jwt = token(register(email()));
        int ownerId = Integer.parseInt(decoder.decode(jwt).getSubject());
        var fields = new java.util.LinkedHashMap<>(rentalFields("Created house", "85.75", "125.45"));
        fields.put("owner_id", "2147483647");
        var created = multipart("POST", "/api/rentals", fields, png(), "../../unsafe.html", "image/png", jwt);
        assertEquals(200, created.statusCode());
        assertEquals("Rental created !", json.readTree(created.body()).get("message").asString());
        Rental rental = rentals.findAll().getFirst();
        try {
            assertEquals(ownerId, rental.getOwnerId());
            assertEquals(new BigDecimal("85.75"), rental.getSurface());
            assertEquals(new BigDecimal("125.45"), rental.getPrice());
            assertTrue(rental.getPicture().matches("/images/[0-9a-f-]{36}\\.png"));
            var image = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + rental.getPicture())).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, image.statusCode());
            assertEquals("image/png", image.headers().firstValue("Content-Type").orElseThrow());
            assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(image.body())));
            var updated = multipart("PUT", "/api/rentals/" + rental.getId(), rentalFields("Updated housing", "95.25", "150.99"),
                    null, null, null, jwt);
            assertEquals(200, updated.statusCode());
            Rental saved = rentals.findById(rental.getId()).orElseThrow();
            assertEquals("Updated housing", saved.getName());
            assertEquals(new BigDecimal("150.99"), saved.getPrice());
            assertEquals(new BigDecimal("95.25"), saved.getSurface());
            assertEquals(ownerId, saved.getOwnerId());
            assertEquals(rental.getPicture(), saved.getPicture());
            assertEquals(rental.getCreatedAt(), saved.getCreatedAt());
            assertFalse(saved.getUpdatedAt().isBefore(saved.getCreatedAt()));
        } finally {
            rentals.deleteById(rental.getId());
            images.delete(rental.getPicture().substring("/images/".length()));
        }
    }

    @Test
    void anotherUserCannotModifyARental() throws Exception {
        String ownerJwt = token(register(email()));
        int ownerId = Integer.parseInt(decoder.decode(ownerJwt).getSubject());
        Rental rental = rentals.saveAndFlush(new Rental("Private edit", BigDecimal.ONE, BigDecimal.TEN,
                "https://example.test/image.png", "Original description", ownerId));
        try {
            String otherJwt = token(register(email()));
            assertEquals(403, multipart("PUT", "/api/rentals/" + rental.getId(), rentalFields("Stolen", "2", "20"),
                    null, null, null, otherJwt).statusCode());
            assertEquals("Private edit", rentals.findById(rental.getId()).orElseThrow().getName());
            assertEquals(404, multipart("PUT", "/api/rentals/2147483647", rentalFields("Missing", "2", "20"),
                    null, null, null, ownerJwt).statusCode());
        } finally {
            rentals.deleteById(rental.getId());
        }
    }
}
