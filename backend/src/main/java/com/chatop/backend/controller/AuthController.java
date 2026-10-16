package com.chatop.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatop.backend.dto.AuthResponse;
import com.chatop.backend.dto.LoginRequest;
import com.chatop.backend.dto.RegisterRequest;
import com.chatop.backend.dto.UserResponse;
import com.chatop.backend.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    /**
     * Construit le contrôleur d’authentification.
     * @param auth service de gestion des comptes et des jetons
     */
    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /**
     * Crée un compte et ouvre une session, sans authentification préalable.
     * @param request nom, email et mot de passe à valider
     * @return jeton JWT du compte créé
     */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    /**
     * Authentifie un utilisateur à partir de ses identifiants.
     * @param request email et mot de passe à vérifier
     * @return jeton JWT si les identifiants sont valides
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /**
     * Retourne le profil de l’utilisateur authentifié.
     * @param jwt jeton validé par Spring Security
     * @return profil sans mot de passe
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(jwt.getSubject());
    }
}
