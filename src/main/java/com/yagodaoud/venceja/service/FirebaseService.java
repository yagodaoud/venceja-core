package com.yagodaoud.venceja.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
    String projectId;

    private Storage storage;

    @PostConstruct
    public void init() {
        try {
            log.info("Inicializando Firebase Storage...");
            log.info("Bucket: {}", bucketName);
            log.info("Project ID: {}", projectId);

            if (bucketName == null || bucketName.isEmpty()) {
                log.warn("Firebase bucket não configurado");
                return;
            }

            if (firebaseCredentials == null) {
                log.warn("Google Credentials não disponíveis");
                return;
            }

            storage = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(firebaseCredentials)
                    .build()
                    .getService();

            log.info("Firebase Storage inicializado com sucesso!");

        } catch (Exception e) {
            log.error("Erro ao inicializar Firebase Storage: {}", e.getMessage(), e);
            log.warn("Firebase Storage não disponível. Uploads retornarão URLs dummy.");
        }
    }

    @PreDestroy
    public void cleanup() {
        // Storage client doesn't always implement Closeable in a way that needs explicit closing for HTTP clients,
        // but if it does:
        if (storage != null) {
            try {
                // storage.close(); // Storage interface doesn't extend AutoCloseable in older versions, but let's check.
                // Actually Google Cloud Storage client is usually stateless-ish or manages its own resources.
                // The original code had storage.close(), so let's try to keep it if possible, or remove if not needed.
                // Checking the original code: it caught exception.
                // Storage interface extends Service<StorageOptions>.
                // In newer libraries it might be AutoCloseable.
                if (storage instanceof AutoCloseable) {
                    ((AutoCloseable) storage).close();
                }
                log.info("Firebase Storage Client fechado");
            } catch (Exception e) {
                log.error("Erro ao fechar Firebase Storage Client", e);
            }
        }
    }

    /**
     * Faz upload de um arquivo e retorna URL assinada
     */
    public String uploadFile(byte[] fileBytes, String fileName, String contentType) throws IOException {
        if (storage == null) {
            return "https://storage.googleapis.com/dummy-bucket/" + fileName;
        }

        try {
            String objectName = "boletos/" + System.currentTimeMillis() + "_" + fileName;

            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .build();

            storage.create(blobInfo, fileBytes);

            // Gera URL assinada válida por 1 ano
            URL signedUrl = storage.signUrl(
                    blobInfo,
                    7,
                    TimeUnit.DAYS,
                    Storage.SignUrlOption.withV4Signature());

            log.info("Arquivo enviado com sucesso: {}", objectName);
            return signedUrl.toString();

        } catch (Exception e) {
            log.error("Erro ao fazer upload do arquivo: {}", e.getMessage(), e);
            throw new IOException("Erro ao fazer upload do arquivo: " + e.getMessage(), e);
        }
    }

    /**
     * Faz upload de imagem de boleto
     */
    public String uploadBoletoImage(byte[] imageBytes, String fileName) throws IOException {
        return uploadFile(imageBytes, fileName, "image/jpeg");
    }

    /**
     * Faz upload de comprovante
     */
    public String uploadComprovante(byte[] fileBytes, String fileName) throws IOException {
        String contentType = fileName.toLowerCase().endsWith(".pdf")
                ? "application/pdf"
                : "image/jpeg";
        return uploadFile(fileBytes, fileName, contentType);
    }

    /**
     * Deleta um arquivo do Firebase utilizando a url
     * @param fileUrl Url completa do arquivo
     * @return true se apagar, false se não encontrar
     */
    public boolean deleteFile(String fileUrl) {
        if (storage == null || fileUrl == null || fileUrl.isEmpty()) {
            log.warn("Firebase Storage não inicializado ou URL inválida");
            return false;
        }

        try {
            String objectName = fileUrl.substring(fileUrl.indexOf("boletos/"));
            
            if (objectName.contains("?")) {
                objectName = objectName.substring(0, objectName.indexOf("?"));
            }
            
            // Decode URL-encoded characters (e.g., %20 to space)
            objectName = java.net.URLDecoder.decode(objectName, java.nio.charset.StandardCharsets.UTF_8);
            
            log.info("Deletando arquivo do Firebase Storage: {}", objectName);
            boolean deleted = storage.delete(BlobId.of(bucketName, objectName));
            
            if (deleted) {
                log.info("Arquivo deletado com sucesso: {}", objectName);
            } else {
                log.warn("Arquivo não encontrado no Firebase Storage: {}", objectName);
            }
            
            return deleted;
        } catch (Exception e) {
            log.error("Erro ao deletar arquivo do Firebase Storage: {}", e.getMessage(), e);
            return false;
        }
    }
}