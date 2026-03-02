package com.example.oldschoolclub.repository;

import com.example.oldschoolclub.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository <Client, Long> {
    Optional<Client> findByTelegramId(Long telegramId);
}
