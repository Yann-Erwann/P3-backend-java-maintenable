package com.chatop.backend.service;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chatop.backend.dto.RentalDetailResponse;
import com.chatop.backend.dto.RentalSummaryResponse;
import com.chatop.backend.dto.RentalsResponse;
import com.chatop.backend.dto.RentalWriteRequest;
import com.chatop.backend.dto.MessageResponse;
import com.chatop.backend.entity.Rental;
import com.chatop.backend.repository.RentalRepository;

@Service
@Transactional(readOnly = true)
public class RentalService {
    private final RentalRepository rentals;
    private final UserService users;
    private final ImageStorageService images;

    public RentalService(RentalRepository rentals, UserService users, ImageStorageService images) {
        this.rentals = rentals;
        this.users = users;
        this.images = images;
    }

    public RentalsResponse list() {
        return new RentalsResponse(rentals.findAll(Sort.by("id")).stream()
                .map(RentalSummaryResponse::from).toList());
    }

    public RentalDetailResponse detail(Integer id) {
        return RentalDetailResponse.from(find(id));
    }

    @Transactional
    public MessageResponse create(RentalWriteRequest request, MultipartFile picture, String subject) {
        Integer ownerId = users.authenticatedId(subject);
        String filename = images.store(picture);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) images.delete(filename);
            }
        });
        rentals.saveAndFlush(new Rental(request.name(), request.surface(), request.price(),
                "/images/" + filename, request.description(), ownerId));
        return new MessageResponse("Rental created !");
    }

    @Transactional
    public MessageResponse update(Integer id, RentalWriteRequest request, String subject) {
        Integer userId = users.authenticatedId(subject);
        Rental rental = find(id);
        if (!rental.getOwnerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can update this rental");
        }
        rental.update(request.name(), request.surface(), request.price(), request.description());
        rentals.flush();
        return new MessageResponse("Rental updated !");
    }

    private Rental find(Integer id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rental id");
        }
        return rentals.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rental not found"));
    }
}
