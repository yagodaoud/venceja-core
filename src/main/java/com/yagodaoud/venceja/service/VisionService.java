package com.yagodaoud.venceja.service;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço para OCR usando Google Cloud Vision API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisionService {

    @Value("${google.vision.api-key:}")
    private String apiKey;

    private static final Pattern VALOR_PATTERN = Pattern.compile(
            "R\\$?\\s*(\\d{1,3}(?:\\.\\d{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VENCIMENTO_PATTERN = Pattern.compile(
            "\\b(\\d{2}/\\d{2}/\\d{4})\\b");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Extrai texto do documento usando OCR
     */
    public String detectDocumentText(byte[] imageBytes) throws IOException {
        try (ImageAnnotatorClient vision = ImageAnnotatorClient.create()) {
            ByteString imgBytes = ByteString.copyFrom(imageBytes);
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            if (responses.isEmpty() || !responses.get(0).hasFullTextAnnotation()) {
                log.warn("OCR não conseguiu extrair texto do documento");
                return "";
            }

            String text = responses.get(0).getFullTextAnnotation().getText();
            log.info("OCR extraiu {} caracteres do documento", text.length());
            return text;
        } catch (Exception e) {
            log.error("Erro ao executar OCR: {}", e.getMessage(), e);
            throw new IOException("Falha ao processar OCR: " + e.getMessage(), e);
        }
    }

    /**
     * Extrai valor do texto OCR
     */
    public BigDecimal extractValor(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }

        Matcher matcher = VALOR_PATTERN.matcher(ocrText);
        if (matcher.find()) {
            String valorStr = matcher.group(1).replace(".", "").replace(",", ".");
            try {
                return new BigDecimal(valorStr);
            } catch (NumberFormatException e) {
                log.warn("Erro ao converter valor: {}", valorStr);
            }
        }

        return null;
    }

    /**
     * Extrai data de vencimento do texto OCR
     */
    public LocalDate extractVencimento(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }

        Matcher matcher = VENCIMENTO_PATTERN.matcher(ocrText);
        while (matcher.find()) {
            String dateStr = matcher.group(1);
            try {
                LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                // Validação básica: data não muito antiga e não muito futura
                if (date.isAfter(LocalDate.now().minusYears(1)) &&
                        date.isBefore(LocalDate.now().plusYears(2))) {
                    return date;
                }
            } catch (DateTimeParseException e) {
                log.debug("Data inválida: {}", dateStr);
            }
        }

        return null;
    }

    /**
     * Extrai fornecedor do texto OCR (primeira linha ou palavras em maiúscula)
     */
    public String extractFornecedor(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }

        String[] lines = ocrText.split("\\n");
        if (lines.length > 0) {
            String firstLine = lines[0].trim();
            if (firstLine.length() > 3 && firstLine.length() < 100) {
                return firstLine;
            }
        }

        // Tenta encontrar palavras em maiúscula
        Pattern pattern = Pattern.compile("\\b[A-ZÁÉÍÓÚÇ][A-ZÁÉÍÓÚÇ\\s]{3,50}\\b");
        Matcher matcher = pattern.matcher(ocrText);
        if (matcher.find()) {
            return matcher.group().trim();
        }

        return "Fornecedor não identificado";
    }

    /**
     * Extrai código de barras do texto OCR
     */
    public String extractCodigoBarras(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }

        // Padrão para código de barras (44 ou 48 dígitos)
        Pattern pattern = Pattern.compile("\\b\\d{44,48}\\b");
        Matcher matcher = pattern.matcher(ocrText.replaceAll("\\s", ""));
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }
}
