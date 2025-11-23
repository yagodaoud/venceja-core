package com.yagodaoud.venceja.config;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;

@Slf4j
@ApplicationScoped
public class JwtService {

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "https://venceja.com")
    String issuer;

    @ConfigProperty(name = "jwt.expiration")
    Long expiration;

    public String generateToken(String username) {
        log.debug("Generating token for user: {} with issuer: {}", username, issuer);

        // NOTICE: No manual file reading or KeySpec logic needed!
        // .sign() automatically picks up 'smallrye.jwt.sign.key.location' from application.properties
        String token = Jwt.issuer(issuer)
                .upn(username)
                .subject(username)
                .groups(new HashSet<>(Arrays.asList("User")))
                .expiresIn(expiration / 1000)
                .sign();

        log.debug("Token generated successfully");
        return token;
    }
}