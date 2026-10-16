package com.chatop.backend.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chatop.backend.dto.AuthResponse;
import com.chatop.backend.dto.LoginRequest;
import com.chatop.backend.dto.RegisterRequest;
import com.chatop.backend.dto.UserResponse;
import com.chatop.backend.entity.User;
import com.chatop.backend.repository.UserRepository;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        checkPasswordLength(request.password());
        if (users.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = users.saveAndFlush(new User(request.email(), request.name(), passwords.encode(request.password())));
        return new AuthResponse(tokens.issue(user.getId()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        checkPasswordLength(request.password());
        User user = users.findByEmail(request.email()).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyPasswordHash : user.getPassword());
        if (!matches || user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new AuthResponse(tokens.issue(user.getId()));
    }

    @Transactional(readOnly = true)
    public UserResponse me(String subject) {
        try {
            return users.findById(Integer.valueOf(subject)).map(UserResponse::from)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user"));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user");
        }
    }

    private void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes");
        }
    }
}
