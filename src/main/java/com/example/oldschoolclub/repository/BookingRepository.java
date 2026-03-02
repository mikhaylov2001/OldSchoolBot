package com.example.oldschoolclub.repository;

import com.example.oldschoolclub.model.Booking;
import com.example.oldschoolclub.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByClientAndStatus(Client client, Booking.BookingStatus status);
    List<Booking> findByDate(LocalDate date);
    boolean existsByZoneIdAndDateAndStartTimeBetween(
            Long zoneId, LocalDate date, LocalTime start, LocalTime end);
    List<Booking> findByDateAndStatus(LocalDate date, Booking.BookingStatus status);

}
