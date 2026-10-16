package com.chatop.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatop.backend.entity.Message;

public interface MessageRepository extends JpaRepository<Message, Integer> {
}
