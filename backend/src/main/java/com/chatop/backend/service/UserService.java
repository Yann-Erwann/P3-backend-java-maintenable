package com.chatop.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chatop.backend.dto.UserResponse;
import com.chatop.backend.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public UserResponse find(Integer id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id");
        }
        return users.findById(id).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public Integer authenticatedId(String subject) {
        try {
            Integer id = Integer.valueOf(subject);
            if (id > 0 && users.existsById(id)) return id;
        } catch (NumberFormatException e) {
            // Un jeton valide doit désigner un utilisateur existant.
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user");
    }
}
