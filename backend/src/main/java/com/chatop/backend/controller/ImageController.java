package com.chatop.backend.controller;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatop.backend.service.ImageStorageService;

@RestController
@RequestMapping("/images")
public class ImageController {
    private final ImageStorageService images;

    /**
     * Construit le contrôleur des images.
     * @param images service de lecture des photos stockées
     */
    public ImageController(ImageStorageService images) {
        this.images = images;
    }

    /**
     * Sert publiquement une photo normalisée pour son affichage dans Angular.
     * @param filename nom UUID avec extension PNG, contrôlé par le service
     * @return image PNG avec cache public d’un jour et protection nosniff
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> read(@PathVariable String filename) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(images.read(filename));
    }
}
