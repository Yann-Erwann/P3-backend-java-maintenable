package com.chatop.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatop.backend.dto.UserResponse;
import com.chatop.backend.service.UserService;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService users;

    /**
     * Construit le contrôleur des profils utilisateurs.
     * @param users service de consultation des utilisateurs
     */
    public UserController(UserService users) {
        this.users = users;
    }

    /**
     * Consulte un profil, notamment celui du propriétaire d’une location.
     * Cette route nécessite une authentification.
     * @param id identifiant de l’utilisateur recherché
     * @return profil sans mot de passe
     */
    @GetMapping("/{id}")
    public UserResponse find(@PathVariable Integer id) {
        return users.find(id);
    }
}
