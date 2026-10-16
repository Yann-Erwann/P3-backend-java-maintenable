package com.chatop.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImageStorageService {
    private static final Logger LOG = LoggerFactory.getLogger(ImageStorageService.class);
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private final Path directory;

    public ImageStorageService(@Value("${app.upload-dir:./uploads}") Path directory) throws IOException {
        Files.createDirectories(directory);
        this.directory = directory.toRealPath();
        if (!Files.isWritable(this.directory)) {
            throw new IOException("Image storage directory is not writable");
        }
    }

    public String store(MultipartFile file) {
        if (file.isEmpty()) throw invalid("An image is required");
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Image exceeds 5 MiB");
        }
        String contentType = file.getContentType();
        if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {
            throw invalid("Only PNG and JPEG images are supported");
        }
        BufferedImage image;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Invalid image content");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("png") && contentType.equals("image/png"))
                        && !((format.equals("jpeg") || format.equals("jpg")) && contentType.equals("image/jpeg"))) {
                    throw invalid("Image content does not match its media type");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw invalid("Image dimensions exceed 4096 pixels");
                }
                image = reader.read(0);
                if (image == null) throw invalid("Invalid image content");
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw invalid("Invalid image content");
        }
        String filename = UUID.randomUUID() + ".png";
        Path destination = directory.resolve(filename);
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            // Réencoder retire les métadonnées et les contenus ajoutés au fichier d’origine.
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
        } catch (IOException e) {
            delete(filename);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image storage unavailable");
        }
        return filename;
    }

    public Resource read(String filename) {
        Path file = resolve(filename);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        return new FileSystemResource(file);
    }

    public void delete(String filename) {
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (IOException e) {
            LOG.warn("Unable to remove an uncommitted rental image");
        }
    }

    private Path resolve(String filename) {
        if (!filename.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        return directory.resolve(filename);
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
