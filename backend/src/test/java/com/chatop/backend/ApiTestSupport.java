package com.chatop.backend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.chatop.backend.repository.MessageRepository;
import com.chatop.backend.repository.RentalRepository;
import com.chatop.backend.repository.UserRepository;
import com.chatop.backend.service.ImageStorageService;

import tools.jackson.databind.json.JsonMapper;

/** Infrastructure HTTP et isolation des données des tests fonctionnels. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(classes = {BackendApplication.class, ApiTestSupport.TestImages.class})
abstract class ApiTestSupport {
    static final String PASSWORD = "Registration-test!42";
    static final Path UPLOADS = temporaryImages();
    final HttpClient client = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    int port;
    @Autowired
    UserRepository users;
    @Autowired
    RentalRepository rentals;
    @Autowired
    MessageRepository messages;
    @Autowired
    ImageStorageService images;
    @Autowired
    PasswordEncoder passwords;
    @Autowired
    JwtDecoder decoder;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        registry.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(bytes));
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    @BeforeEach
    void resetTestData() throws IOException {
        messages.deleteAllInBatch();
        rentals.deleteAllInBatch();
        users.deleteAllInBatch();
        try (var files = Files.list(UPLOADS)) {
            for (Path file : files.toList()) Files.delete(file);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestImages {
        @Bean
        DisposableBean temporaryImageCleanup() {
            return ApiTestSupport::removeTemporaryImages;
        }
    }

    private static Path temporaryImages() {
        try {
            return Files.createTempDirectory("chatop-images-test-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void removeTemporaryImages() throws IOException {
        try (var files = Files.walk(UPLOADS)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }

    Map<String, String> rentalFields(String name, String surface, String price) {
        return Map.of("name", name, "surface", surface, "price", price, "description", "Description for the rental");
    }

    byte[] png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    HttpResponse<String> multipart(String method, String path, Map<String, String> fields,
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

    HttpResponse<String> post(String path, Map<String, ?> body, String jwt) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String email() { return UUID.randomUUID() + "@example.test"; }

    HttpResponse<String> register(String email) throws Exception {
        return post("/api/auth/register", Map.of("email", email, "name", "Test User", "password", PASSWORD));
    }

    String token(HttpResponse<String> response) {
        return json.readTree(response.body()).get("token").asString();
    }

    HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        return post(path, body, null);
    }

    HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
