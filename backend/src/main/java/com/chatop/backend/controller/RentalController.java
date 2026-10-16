package com.chatop.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;

import com.chatop.backend.dto.RentalDetailResponse;
import com.chatop.backend.dto.RentalsResponse;
import com.chatop.backend.service.RentalService;

@RestController
@RequestMapping("/api/rentals")
public class RentalController {
    private final RentalService rentals;

    /**
     * Construit le contrôleur des locations.
     * @param rentals service de gestion des locations
     */
    public RentalController(RentalService rentals) {
        this.rentals = rentals;
    }

    /**
     * Liste les locations pour un utilisateur authentifié.
     * @return locations triées par identifiant croissant
     */
    @GetMapping
    public RentalsResponse list() {
        return rentals.list();
    }

    /**
     * Consulte une location pour un utilisateur authentifié.
     * @param id identifiant de la location recherchée
     * @return détail de la location, avec ses chemins d’images
     */
    @GetMapping("/{id}")
    public RentalDetailResponse detail(@PathVariable Integer id) {
        return rentals.detail(id);
    }

}
