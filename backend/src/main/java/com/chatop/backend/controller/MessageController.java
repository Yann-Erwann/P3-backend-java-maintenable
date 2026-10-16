package com.chatop.backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import com.chatop.backend.dto.MessageRequest;
import com.chatop.backend.dto.MessageResponse;
import com.chatop.backend.service.MessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messages;

    /**
     * Construit le contrôleur des messages.
     * @param messages service d’enregistrement des messages
     */
    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    /**
     * Enregistre un message après vérification que son auteur correspond au JWT.
     * @param request identifiants de la location et de l’auteur, et texte du message
     * @param jwt jeton validé de l’utilisateur envoyant le message
     * @return confirmation d’enregistrement, sans envoi d’email
     */
    @PostMapping
    public MessageResponse send(@Valid @RequestBody MessageRequest request, @AuthenticationPrincipal Jwt jwt) {
        return messages.send(request, jwt.getSubject());
    }
}
