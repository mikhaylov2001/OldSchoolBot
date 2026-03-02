package com.example.oldschoolclub.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "zones")
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;  // "Обычный стол" / "PS Зона"

    private String description;

    private Double pricePerHour;

    private boolean active = true;


}
