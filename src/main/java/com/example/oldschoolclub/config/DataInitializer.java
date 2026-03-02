package com.example.oldschoolclub.config;


import com.example.oldschoolclub.model.Zone;
import com.example.oldschoolclub.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ZoneRepository zoneRepository;

    @Override
    public void run(String... args) {
        if (zoneRepository.count() == 0) {
            Zone table = new Zone();
            table.setName("\uD83D\uDDA5\uFE0E Геймерский стол");
            table.setDescription("Обычный стол для настольных игр");
            table.setPricePerHour(350.0);
            zoneRepository.save(table);

            Zone ps = new Zone();
            ps.setName("🎮  PS Зона");
            ps.setDescription("PlayStation 5 — играй в лучшие игры");
            ps.setPricePerHour(500.0);
            zoneRepository.save(ps);

            System.out.println("✅ Зоны созданы!");
        }
    }
}
