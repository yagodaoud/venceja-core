package com.yagodaoud.venceja.config;

import com.google.auth.oauth2.GoogleCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Configuração centralizada de credenciais do Firebase
 */
@Slf4j
@ApplicationScoped
public class FirebaseCredentialsConfig {

    @ConfigProperty(name = "firebase.credentials.path")
    Optional<String> credentialsPath;

            if (credentials != null) {
                credentials = credentials.createScoped(
                        "https://www.googleapis.com/auth/cloud-platform"
                );
                log.info("Firebase Credentials carregadas com sucesso!");
            }
            return credentials;

        } catch (Exception e) {
            log.error("Erro ao carregar credenciais do Firebase: {}", e.getMessage(), e);
            log.warn("Serviços do Firebase podem não funcionar corretamente");
            return null;
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        // Opção 1: JSON inline (variável de ambiente - melhor para produção)
        if (credentialsJson.isPresent() && !credentialsJson.get().isEmpty()) {
            log.info("Carregando credenciais do JSON inline (variável de ambiente)");
            ByteArrayInputStream stream = new ByteArrayInputStream(
                    credentialsJson.get().getBytes(StandardCharsets.UTF_8)
            );
            return GoogleCredentials.fromStream(stream);
        }

        // Opção 2: Arquivo local (caminho absoluto ou relativo)
        if (credentialsPath.isPresent() && !credentialsPath.get().isEmpty()) {
            log.info("Carregando credenciais do arquivo: {}", credentialsPath.get());
            try (FileInputStream stream = new FileInputStream(credentialsPath.get())) {
                return GoogleCredentials.fromStream(stream);
            }
        }

        // Opção 3: Arquivo em resources (padrão para desenvolvimento)
        log.info("Carregando credenciais do resource: {}", credentialsResource);
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(credentialsResource)) {

            if (stream == null) {
                // Try class loader
                try (InputStream stream2 = getClass().getClassLoader().getResourceAsStream(credentialsResource)) {
                    if (stream2 == null) {
                         throw new IOException(
                                "Arquivo " + credentialsResource + " não encontrado em resources. " +
                                        "Configure FIREBASE_CREDENTIALS_PATH ou FIREBASE_CREDENTIALS_JSON"
                        );
                    }
                    return GoogleCredentials.fromStream(stream2);
                }
            }

            return GoogleCredentials.fromStream(stream);
        }
    }
}