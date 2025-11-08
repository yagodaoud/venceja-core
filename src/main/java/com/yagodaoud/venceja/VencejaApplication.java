package com.yagodaoud.venceja;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principal da aplicação VenceJa
 */
@SpringBootApplication
@EnableScheduling
public class VencejaApplication {

    public static void main(String[] args) {
        SpringApplication.run(VencejaApplication.class, args);
    }
}
