package com.example.oldschoolclub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OldSchoolClubApplication {

    public static void main(String[] args) {
        SpringApplication.run(OldSchoolClubApplication.class, args);
    }

}
