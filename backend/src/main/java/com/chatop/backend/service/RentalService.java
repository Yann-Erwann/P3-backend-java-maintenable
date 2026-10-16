package com.chatop.backend.service;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.chatop.backend.dto.RentalDetailResponse;
import com.chatop.backend.dto.RentalSummaryResponse;
import com.chatop.backend.dto.RentalsResponse;
import com.chatop.backend.entity.Rental;
import com.chatop.backend.repository.RentalRepository;

@Service
@Transactional(readOnly = true)
public class RentalService {
    private final RentalRepository rentals;

    public RentalService(RentalRepository rentals) {
        this.rentals = rentals;
    }

    public RentalsResponse list() {
        return new RentalsResponse(rentals.findAll(Sort.by("id")).stream()
                .map(RentalSummaryResponse::from).toList());
    }

    public RentalDetailResponse detail(Integer id) {
        return RentalDetailResponse.from(find(id));
    }

    private Rental find(Integer id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rental id");
        }
        return rentals.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rental not found"));
    }
}
