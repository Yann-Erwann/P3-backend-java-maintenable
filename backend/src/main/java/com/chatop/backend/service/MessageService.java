package com.chatop.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chatop.backend.dto.MessageRequest;
import com.chatop.backend.dto.MessageResponse;
import com.chatop.backend.entity.Message;
import com.chatop.backend.repository.MessageRepository;
import com.chatop.backend.repository.RentalRepository;

@Service
public class MessageService {
    private final MessageRepository messages;
    private final RentalRepository rentals;
    private final UserService users;

    public MessageService(MessageRepository messages, RentalRepository rentals, UserService users) {
        this.messages = messages;
        this.rentals = rentals;
        this.users = users;
    }

    @Transactional
    public MessageResponse send(MessageRequest request, String subject) {
        Integer userId = users.authenticatedId(subject);
        if (!userId.equals(request.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Message author does not match authenticated user");
        }
        if (!rentals.existsById(request.rentalId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rental not found");
        }
        messages.saveAndFlush(new Message(request.rentalId(), userId, request.message()));
        return new MessageResponse("Message send with success");
    }
}
