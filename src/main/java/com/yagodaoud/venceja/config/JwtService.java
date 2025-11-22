package com.yagodaoud.venceja.config;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Serviço para geração de JWT tokens usando SmallRye JWT
 */
@ApplicationScoped
public class JwtService {

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "https://venceja.com")
    String issuer;

    @ConfigProperty(name = "jwt.expiration")
    Long expiration;

    /**
     * Gera um token JWT para o usuário.
     * @param username O email do usuário (usado como upn e subject)
     * @return O token JWT assinado
     */
    public String generateToken(String username) {
        return Jwt.issuer(issuer)
                .upn(username)
                .subject(username)
                .groups(new HashSet<>(Arrays.asList("User"))) // Adiciona role padrão "User"
                .expiresIn(expiration / 1000) // expiration em ms, expiresIn espera segundos
                .sign();
    }

    // Validação é feita automaticamente pelo Quarkus SmallRye JWT
    // Métodos de extração manuais não são mais necessários pois SecurityIdentity injeta o Principal
}
