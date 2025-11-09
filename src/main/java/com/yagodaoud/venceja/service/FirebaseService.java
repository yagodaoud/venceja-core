package com.yagodaoud.venceja.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Serviço para upload de arquivos no Firebase Storage
 * Usa as mesmas credenciais do Google Cloud (Vision API)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseService {

    private final GoogleCredentials firebaseCredentials;

    @Value("${firebase.storage.bucket:}")
    private String bucketName;

    @Value("${firebase.storage.project-id:}")
    private String projectId;

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
}