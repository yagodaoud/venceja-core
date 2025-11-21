package com.yagodaoud.venceja.config;

import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Configuração centralizada de credenciais do Google Cloud
 * Serve para Vision API, Storage, e outros serviços do Google Cloud
 */
@Slf4j
@Configuration
public class GoogleCredentialsConfig {

    @Value("${google.credentials.path:}")
    private String credentialsPath;

    @Value("${google.credentials.json:}")
    private String credentialsJson;

    @Value("${google.credentials.resource:}")
    private String credentialsResource;

    /**
     * Bean único de GoogleCredentials compartilhado por todos os serviços
     */
    @Bean
    public GoogleCredentials googleCredentials() {
        try {
            log.info("Inicializando Google Cloud Credentials...");

            GoogleCredentials credentials = loadCredentials();

            // Adiciona escopo necessário para todos os serviços
            credentials = credentials.createScoped(
                    "https://www.googleapis.com/auth/cloud-platform"
            );

            // Força refresh para evitar tokens expirados ocupando memória
            credentials.refresh();

            log.info("Google Cloud Credentials carregadas com sucesso!");
            return credentials;

        } catch (Exception e) {
            log.error("Erro ao carregar credenciais do Google Cloud: {}", e.getMessage(), e);
            log.warn("Serviços do Google Cloud podem não funcionar corretamente");
            return null;
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        // Opção 1: JSON inline (variável de ambiente - melhor para produção)
        if (credentialsJson != null && !credentialsJson.isEmpty()) {
            log.info("Carregando credenciais do JSON inline (variável de ambiente)");
            ByteArrayInputStream stream = new ByteArrayInputStream(
                    credentialsJson.getBytes(StandardCharsets.UTF_8)
            );
            return GoogleCredentials.fromStream(stream);
        }

        // Opção 2: Arquivo local (caminho absoluto ou relativo)
        if (credentialsPath != null && !credentialsPath.isEmpty()) {
            log.info("Carregando credenciais do arquivo: {}", credentialsPath);
            try (FileInputStream stream = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(stream);
            }
        }

        // Opção 3: Arquivo em resources (padrão para desenvolvimento)
        log.info("Carregando credenciais do resource: {}", credentialsResource);
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(credentialsResource)) {

            if (stream == null) {
                throw new IOException(
                        "Arquivo " + credentialsResource + " não encontrado em resources. " +
                                "Configure GOOGLE_CREDENTIALS_PATH ou GOOGLE_CREDENTIALS_JSON"
                );
            }

            return GoogleCredentials.fromStream(stream);
        }
    }
}