package com.yagodaoud.venceja.config;

import com.google.auth.oauth2.GoogleCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Configuração centralizada de credenciais do Google Cloud
 * Serve para Vision API, Storage, e outros serviços do Google Cloud
 */
@Slf4j
@ApplicationScoped
public class GoogleCredentialsConfig {

    @ConfigProperty(name = "google.credentials.path")
    Optional<String> credentialsPath;

    @ConfigProperty(name = "google.credentials.json")
    Optional<String> credentialsJson;

    @ConfigProperty(name = "google.credentials.resource", defaultValue = "venceja-google.json")
    String credentialsResource;

    /**
     * Bean único de GoogleCredentials compartilhado por todos os serviços
     */
    @Produces
    @ApplicationScoped
    @Named("google")
    public GoogleCredentials googleCredentials() {
        try {
            log.info("Inicializando Google Cloud Credentials...");

            GoogleCredentials credentials = loadCredentials();

            // Adiciona escopo necessário para todos os serviços
            if (credentials != null) {
                credentials = credentials.createScoped(
                        "https://www.googleapis.com/auth/cloud-platform"
                );

                // Força refresh para evitar tokens expirados ocupando memória
                credentials.refresh();

                log.info("Google Cloud Credentials carregadas com sucesso!");
            }
            return credentials;

        } catch (Exception e) {
            log.error("Erro ao carregar credenciais do Google Cloud: {}", e.getMessage(), e);
            log.warn("Serviços do Google Cloud podem não funcionar corretamente");
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
                                        "Configure GOOGLE_CREDENTIALS_PATH ou GOOGLE_CREDENTIALS_JSON"
                        );
                    }
                    return GoogleCredentials.fromStream(stream2);
                }
            }

            return GoogleCredentials.fromStream(stream);
        }
    }
}