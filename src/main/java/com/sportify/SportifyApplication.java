package com.sportify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SportifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SportifyApplication.class, args);
    }

}
