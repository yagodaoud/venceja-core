package com.yagodaoud.venceja;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Testes básicos da aplicação
 */
@SpringBootTest
@ActiveProfiles("dev")
class VencejaApplicationTests {

    @Test
    void contextLoads() {
        // Testa se o contexto do Spring Boot carrega corretamente
    }
}
