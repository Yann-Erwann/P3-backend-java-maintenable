package com.chatop.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import com.chatop.backend.dto.RentalWriteRequest;
import com.chatop.backend.service.RentalService;

class ImageTests extends ApiTestSupport {
    @Autowired
    RentalService rentalService;

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
    }

    @Test
    void missingStaticImagesReturn404WithoutRequiringAuthentication() throws Exception {
        for (String filename : java.util.List.of("not-a-generated-image.png",
                "00000000-0000-0000-0000-000000000000.png")) {
            for (String method : java.util.List.of("GET", "HEAD")) {
                var response = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/images/" + filename))
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(404, response.statusCode());
                if (method.equals("HEAD")) assertTrue(response.body().isEmpty());
            }
        }
        assertEquals(401, get("/error", null).statusCode());
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
    void imagesAllowPublicReadsFromConfiguredOrigins() throws Exception {
        String filename = images.store(new MockMultipartFile("picture", "image.png", "image/png", png()));
        try {
            for (String origin : java.util.List.of("http://localhost:8081", "http://127.0.0.1:8081",
                    "http://localhost:4200", "http://127.0.0.1:4200")) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/images/" + filename))
                        .header("Origin", origin).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, response.statusCode());
                assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
                assertEquals("image/png", response.headers().firstValue("Content-Type").orElseThrow());
                assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
                String cacheControl = response.headers().firstValue("Cache-Control").orElseThrow();
                assertTrue(cacheControl.contains("max-age=86400"));
                assertTrue(cacheControl.contains("public"));
                assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(response.body())));
            }
            var head = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/images/" + filename))
                    .header("Origin", "http://localhost:8081").method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, head.statusCode());
            assertEquals(0, head.body().length);
            assertEquals("http://localhost:8081", head.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            assertTrue(head.headers().firstValue("Cache-Control").orElseThrow().contains("public"));
        } finally {
            images.delete(filename);
        }
    }

    @Test
    void imagesRejectOtherOriginsAndWritePreflights() throws Exception {
        var uri = URI.create("http://127.0.0.1:" + port + "/images/00000000-0000-0000-0000-000000000000.png");
        var denied = client.send(HttpRequest.newBuilder(uri).header("Origin", "https://untrusted.example")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, denied.statusCode());
        assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        var write = client.send(HttpRequest.newBuilder(uri).header("Origin", "http://localhost:8081")
                .header("Access-Control-Request-Method", "PUT")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, write.statusCode());
    }
}
