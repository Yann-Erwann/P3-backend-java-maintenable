package com.chatop.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DataIntegrityViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.chatop.backend.repository.UserRepository;
import com.chatop.backend.repository.RentalRepository;
import com.chatop.backend.entity.Rental;
import com.chatop.backend.entity.User;
import com.chatop.backend.service.ImageStorageService;
import com.chatop.backend.service.RentalService;
import com.chatop.backend.dto.RentalWriteRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApplicationTests {
    private static final String PASSWORD = "Registration-test!42";
    private static final Path UPLOADS = temporaryImages();
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;
    @Autowired
    RentalRepository rentals;
    @Autowired
    ImageStorageService images;
    @Autowired
    RentalService rentalService;
    @Autowired
    PasswordEncoder passwords;
    @Autowired
    JwtDecoder decoder;
    @Autowired
    JwtEncoder encoder;

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        registry.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(bytes));
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path temporaryImages() {
        try {
            return Files.createTempDirectory("chatop-images-test-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void removeTemporaryImages() throws IOException {
        try (var files = Files.walk(UPLOADS)) {
            for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }

    @Test
    void registerPersistsHashAndIssuesUsableJwtWithoutExposingPassword() throws Exception {
        String email = email();
        var response = register(email);
        assertEquals(200, response.statusCode());
        String token = token(response);
        var user = users.findByEmail(email).orElseThrow();
        assertNotEquals(PASSWORD, user.getPassword());
        assertTrue(passwords.matches(PASSWORD, user.getPassword()));
        assertEquals(user.getId().toString(), decoder.decode(token).getSubject());
        var profile = get("/api/auth/me", token);
        assertEquals(200, profile.statusCode());
        JsonNode body = json.readTree(profile.body());
        assertEquals(email, body.get("email").asText());
        assertEquals("Test User", body.get("name").asText());
        assertTrue(body.has("created_at"));
        assertTrue(body.has("updated_at"));
        assertFalse(body.has("password"));
        assertFalse(response.body().contains(PASSWORD));
    }

    @Test
    void loginNormalizesEmailAndReturnsAUsableToken() throws Exception {
        String email = email();
        assertEquals(200, register(email).statusCode());
        var response = post("/api/auth/login", Map.of("email", " " + email.toUpperCase() + " ", "password", PASSWORD));
        assertEquals(200, response.statusCode());
        assertEquals(200, get("/api/auth/me", token(response)).statusCode());
    }

    @Test
    void duplicateEmailIsRejectedWithoutCreatingAnotherUser() throws Exception {
        String email = email();
        assertEquals(200, register(email).statusCode());
        long count = users.count();
        var duplicate = register(email.toUpperCase());
        assertEquals(409, duplicate.statusCode());
        assertEquals(count, users.count());
        assertFalse(duplicate.body().contains("password"));
    }

    @Test
    void simultaneousRegistrationsCannotCreateTwoAccountsWithTheSameEmail() throws Exception {
        String email = email();
        var body = Map.of("email", email, "name", "Concurrent User", "password", PASSWORD);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/register"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var statuses = java.util.List.of(first.get().statusCode(), second.get().statusCode())
                .stream().sorted().toList();
        assertEquals(java.util.List.of(200, 409), statuses);
        assertTrue(users.findByEmail(email).isPresent());
    }

    @Test
    void wrongPasswordAndUnknownUserReturnTheSameError() throws Exception {
        String email = email();
        assertEquals(200, register(email).statusCode());
        var wrong = post("/api/auth/login", Map.of("email", email, "password", "wrong-password"));
        var unknown = post("/api/auth/login", Map.of("email", email(), "password", "wrong-password"));
        assertEquals(401, wrong.statusCode());
        assertEquals(401, unknown.statusCode());
        assertEquals(wrong.body(), unknown.body());
    }

    @Test
    void invalidAndMissingFieldsAreRejectedWithoutPersisting() throws Exception {
        long count = users.count();
        for (var input : java.util.List.of(
                Map.of("email", "invalid", "name", "Test", "password", PASSWORD),
                Map.of("email", email(), "name", " ", "password", PASSWORD),
                Map.of("email", email(), "name", "Test", "password", "short"),
                Map.of("email", email(), "name", "Test", "password", "é".repeat(40)),
                Map.<String, String>of())) {
            assertEquals(400, post("/api/auth/register", input).statusCode());
        }
        assertEquals(400, post("/api/auth/login", Map.of()).statusCode());
        assertEquals(count, users.count());
    }

    @Test
    void missingAndMockTokensCannotAccessProtectedRoutes() throws Exception {
        for (String route : java.util.List.of("/api/auth/me", "/api/rentals", "/api/rentals/1", "/api/user/1", "/api/messages")) {
            assertEquals(401, get(route, null).statusCode());
            assertEquals(401, get(route, "jwt").statusCode());
        }
    }

    @Test
    void expiredWrongIssuerAndTamperedTokensAreRejected() throws Exception {
        Instant now = Instant.now();
        String expired = signedToken("chatop", now.minusSeconds(3600), now.minusSeconds(120));
        String wrongIssuer = signedToken("another-issuer", now, now.plusSeconds(3600));
        String valid = token(register(email()));
        String[] parts = valid.split("\\.");
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"999\",\"iss\":\"chatop\"}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + payload + "." + parts[2];
        for (String token : java.util.List.of(expired, wrongIssuer, tampered)) {
            assertEquals(401, get("/api/auth/me", token).statusCode());
        }
    }

    @Test
    void angularAndSwaggerPreflightAllowOnlyTheConfiguredOrigins() throws Exception {
        for (String origin : java.util.List.of("http://localhost:8081", "http://127.0.0.1:8081",
                "http://localhost:4200", "http://127.0.0.1:4200", "https://untrusted.example")) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                    .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(origin.equals("https://untrusted.example") ? 403 : 200, response.statusCode());
            if (response.statusCode() == 200) {
                assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            } else {
                assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            }
        }
    }

    @Test
    void registerAcceptsTheAngularOriginForwardedByTheProxy() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/register"))
                .header("Origin", "http://localhost:4200").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("email", email(), "name", "Proxy User", "password", PASSWORD))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:4200", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals(200, get("/api/auth/me", token(response)).statusCode());
    }

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
            assertEquals("First house", items.get(0).get("name").asText());
            assertEquals("https://example.test/house.jpg", items.get(0).get("picture").asText());
            assertEquals(owner.getId().intValue(), items.get(0).get("owner_id").asInt());
            assertFalse(items.get(0).has("owner"));
            assertFalse(response.body().contains("password"));

            var detail = get("/api/rentals/" + first.getId(), jwt);
            assertEquals(200, detail.statusCode());
            JsonNode body = json.readTree(detail.body());
            assertEquals("First house", body.get("name").asText());
            assertEquals(120, body.get("surface").asInt());
            assertEquals(300, body.get("price").asInt());
            assertEquals("First description", body.get("description").asText());
            assertTrue(body.get("picture").isArray());
            assertEquals(1, body.get("picture").size());
            assertEquals("https://example.test/house.jpg", body.get("picture").get(0).asText());
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
        assertEquals("Public Owner", body.get("name").asText());
        assertEquals(owner.getEmail(), body.get("email").asText());
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
        assertEquals("Rental created !", json.readTree(created.body()).get("message").asText());
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

    @Test
    void invalidFieldsAndImagesDoNotCreateRentalsOrFiles() throws Exception {
        String jwt = token(register(email()));
        long count = rentals.count();
        long files;
        try (var paths = Files.list(UPLOADS)) { files = paths.count(); }
        for (var fields : java.util.List.of(rentalFields(" ", "10", "20"), rentalFields("House", "0", "20"),
                rentalFields("House", "10", "-1"), rentalFields("House", "10", "1.234"),
                rentalFields("House", "not-a-number", "20"), rentalFields("House", "10", "10000000000"))) {
            assertEquals(400, multipart("POST", "/api/rentals", fields, png(), "image.png", "image/png", jwt).statusCode());
        }
        assertEquals(400, multipart("POST", "/api/rentals", rentalFields("House", "10", "20"), null, null, null, jwt).statusCode());
        assertEquals(400, multipart("POST", "/api/rentals", rentalFields("House", "10", "20"), new byte[0], "empty.png", "image/png", jwt).statusCode());
        assertEquals(400, multipart("POST", "/api/rentals", rentalFields("House", "10", "20"),
                "<script>bad</script>".getBytes(StandardCharsets.UTF_8), "fake.png", "image/png", jwt).statusCode());
        assertEquals(400, multipart("POST", "/api/rentals", rentalFields("House", "10", "20"), png(), "image.png", "text/html", jwt).statusCode());
        var oversizedDimensions = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", oversizedDimensions);
        assertEquals(400, multipart("POST", "/api/rentals", rentalFields("House", "10", "20"),
                oversizedDimensions.toByteArray(), "wide.png", "image/png", jwt).statusCode());
        assertEquals(413, multipart("POST", "/api/rentals", rentalFields("House", "10", "20"),
                new byte[5 * 1024 * 1024 + 1], "large.png", "image/png", jwt).statusCode());
        assertEquals(count, rentals.count());
        try (var paths = Files.list(UPLOADS)) { assertEquals(files, paths.count()); }
        assertEquals(404, get("/images/not-a-generated-image.png", null).statusCode());
    }

    @Test
    void databaseRollbackAlsoRemovesTheNewImage() throws Exception {
        String jwt = token(register(email()));
        var file = new MockMultipartFile("picture", "image.png", "image/png", png());
        long before;
        try (var paths = Files.list(UPLOADS)) { before = paths.count(); }
        // Une défaillance SQL après l’écriture du fichier doit déclencher son nettoyage.
        assertThrows(DataIntegrityViolationException.class, () -> rentalService.create(
                new RentalWriteRequest("x".repeat(256), BigDecimal.ONE, BigDecimal.ONE, "Description"),
                file, decoder.decode(jwt).getSubject()));
        try (var paths = Files.list(UPLOADS)) { assertEquals(before, paths.count()); }
    }

    @Test
    void writeRoutesCannotBeUsedWithoutAuthentication() throws Exception {
        assertEquals(401, multipart("POST", "/api/rentals", rentalFields("House", "10", "20"), png(), "image.png", "image/png", null).statusCode());
        assertEquals(401, multipart("PUT", "/api/rentals/1", rentalFields("House", "10", "20"), null, null, null, null).statusCode());
        assertEquals(401, post("/api/messages", Map.of("rental_id", "1", "user_id", "1", "message", "Hello"), null).statusCode());
    }

    private Map<String, String> rentalFields(String name, String surface, String price) {
        return Map.of("name", name, "surface", surface, "price", price, "description", "Description for the rental");
    }

    private byte[] png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private HttpResponse<String> multipart(String method, String path, Map<String, String> fields,
            byte[] image, String filename, String mediaType, String jwt) throws Exception {
        String boundary = "chatop-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        for (var field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                    + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if (image != null) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"picture\"; filename=\""
                    + filename + "\"\r\nContent-Type: " + mediaType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(image);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return client.send(request.method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, String> body, String jwt) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String signedToken(String issuer, Instant issued, Instant expires) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(issued).expiresAt(expires).build()))
                .getTokenValue();
    }

    private String email() { return UUID.randomUUID() + "@example.test"; }

    private HttpResponse<String> register(String email) throws Exception {
        return post("/api/auth/register", Map.of("email", email, "name", "Test User", "password", PASSWORD));
    }

    private String token(HttpResponse<String> response) {
        return json.readTree(response.body()).get("token").asText();
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
