package com.chatop.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatop.backend.entity.Rental;

public interface RentalRepository extends JpaRepository<Rental, Integer> {
}
