package com.example.oldschoolclub.repository;

import com.example.oldschoolclub.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByActiveTrue();
}
