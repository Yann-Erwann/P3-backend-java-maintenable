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
import com.chatop.backend.dto.RentalWriteRequest;
import com.chatop.backend.dto.MessageResponse;

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

    /**
     * Crée une location appartenant à l’utilisateur authentifié.
     * @param request champs de la location transmis en multipart
     * @param picture photo PNG ou JPEG à valider et à stocker
     * @param jwt jeton validé dont le sujet identifie le propriétaire
     * @return confirmation de création
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MessageResponse create(@Valid @ModelAttribute RentalWriteRequest request,
            @RequestParam("picture") MultipartFile picture, @AuthenticationPrincipal Jwt jwt) {
        return rentals.create(request, picture, jwt.getSubject());
    }

    /**
     * Modifie une location après vérification de sa propriété par le service.
     * L’image et le propriétaire sont conservés.
     * @param id identifiant de la location à modifier
     * @param request nouvelles valeurs transmises en multipart
     * @param jwt jeton validé de l’utilisateur demandant la modification
     * @return confirmation de modification
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MessageResponse update(@PathVariable Integer id, @Valid @ModelAttribute RentalWriteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return rentals.update(id, request, jwt.getSubject());
    }
}
