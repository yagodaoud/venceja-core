package com.yagodaoud.venceja.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
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

    private final GoogleCredentials googleCredentials;

    private ImageAnnotatorClient visionClient;

    private static final Pattern VALOR_PATTERN = Pattern.compile(
            "R\\$?\\s*(\\d{1,3}(?:\\.\\d{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VENCIMENTO_PATTERN = Pattern.compile(
            "\\b(\\d{2}/\\d{2}/\\d{4})\\b");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @PostConstruct
    public void init() {
        try {
            log.info("Inicializando Google Vision Client...");

            if (googleCredentials == null) {
                log.warn("Google Credentials não disponíveis");
                return;
            }

            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(() -> googleCredentials)
                    .build();

            visionClient = ImageAnnotatorClient.create(settings);

            log.info("Google Vision Client inicializado com sucesso!");

        } catch (Exception e) {
            log.error("Erro ao inicializar Vision Client: {}", e.getMessage(), e);
            log.warn("OCR não estará disponível");
        }
    }

    /**
     * Extrai texto do documento usando OCR
     */
    public String detectDocumentText(byte[] imageBytes) throws IOException {
        if (visionClient == null) {
            throw new IOException("Vision Client não inicializado. Verifique as credenciais do Google Cloud.");
        }

        try {
            ByteString imgBytes = ByteString.copyFrom(imageBytes);
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            BatchAnnotateImagesResponse response = visionClient.batchAnnotateImages(requests);
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
    /**
     * Extrai código de barras ou linha digitável do texto OCR
     * Suporta ambos os formatos:
     * - Linha digitável: 47 dígitos com separadores (ex: 75691.31951 01017.799709...)
     * - Código de barras: 44 ou 48 dígitos contínuos
     */
    public String extractCodigoBarras(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }

        // Remove quebras de linha para facilitar matching
        String cleanText = ocrText.replaceAll("\\r?\\n", " ");

        // 1. Tenta encontrar linha digitável (47 dígitos com separadores)
        // Formato: XXXXX.XXXXX XXXXX.XXXXXX XXXXX.XXXXXX X XXXXXXXXXXXXXX
        // Campos: 5.5 5.6 5.6 1 14
        Pattern linhaDigitavelPattern = Pattern.compile(
                "\\b(\\d{5}\\.\\d{5})\\s+(\\d{5}\\.\\d{6})\\s+(\\d{5}\\.\\d{6})\\s+(\\d)\\s+(\\d{14})\\b"
        );
        Matcher linhaDigitavelMatcher = linhaDigitavelPattern.matcher(cleanText);

        if (linhaDigitavelMatcher.find()) {
            // Concatena todos os grupos removendo os pontos
            String campo1 = linhaDigitavelMatcher.group(1).replace(".", "");
            String campo2 = linhaDigitavelMatcher.group(2).replace(".", "");
            String campo3 = linhaDigitavelMatcher.group(3).replace(".", "");
            String campo4 = linhaDigitavelMatcher.group(4);
            String campo5 = linhaDigitavelMatcher.group(5);

            String linhaDigitavel = campo1 + campo2 + campo3 + campo4 + campo5;
            log.info("Linha digitável encontrada: {}", linhaDigitavel);
            return linhaDigitavel;
        }

        // 2. Tenta padrão mais flexível para linha digitável
        // Aceita variações com ou sem pontos/espaços
        Pattern flexivelPattern = Pattern.compile(
                "\\b(\\d{5})[.\\s]?(\\d{5})[\\s]+(\\d{5})[.\\s]?(\\d{6})[\\s]+(\\d{5})[.\\s]?(\\d{6})[\\s]+(\\d)[\\s]+(\\d{14})\\b"
        );
        Matcher flexivelMatcher = flexivelPattern.matcher(cleanText);

        if (flexivelMatcher.find()) {
            StringBuilder linhaDigitavel = new StringBuilder();
            for (int i = 1; i <= flexivelMatcher.groupCount(); i++) {
                linhaDigitavel.append(flexivelMatcher.group(i));
            }
            log.info("Linha digitável (formato flexível) encontrada: {}", linhaDigitavel);
            return linhaDigitavel.toString();
        }

        // 3. Tenta encontrar código de barras puro (44 ou 48 dígitos contínuos)
        String textWithoutSpaces = cleanText.replaceAll("\\s", "");
        Pattern codigoBarrasPattern = Pattern.compile("\\b\\d{44,48}\\b");
        Matcher codigoBarrasMatcher = codigoBarrasPattern.matcher(textWithoutSpaces);

        if (codigoBarrasMatcher.find()) {
            String codigoBarras = codigoBarrasMatcher.group();
            log.info("Código de barras encontrado: {}", codigoBarras);
            return codigoBarras;
        }

        // 4. Última tentativa: procura sequências de dígitos que pareçam ser linha digitável
        // mas podem ter OCR com erros (pontos faltando, etc)
        Pattern sequenciaPattern = Pattern.compile("\\b\\d{5,6}[.\\s]+\\d{5,6}[.\\s]+\\d{5,6}[.\\s]+\\d{5,6}[.\\s]+\\d{5,6}[.\\s]+\\d{5,6}[.\\s]+\\d[.\\s]+\\d{14}\\b");
        Matcher sequenciaMatcher = sequenciaPattern.matcher(cleanText);

        if (sequenciaMatcher.find()) {
            String sequencia = sequenciaMatcher.group().replaceAll("[.\\s]", "");
            log.info("Sequência de dígitos encontrada: {}", sequencia);
            return sequencia;
        }

        log.warn("Nenhum código de barras ou linha digitável encontrado no texto OCR");
        return null;
    }

    /**
     * Converte linha digitável (47 dígitos) para código de barras (44 dígitos)
     * Útil se você precisar do formato de código de barras real
     */
    public String linhaDigitavelParaCodigoBarras(String linhaDigitavel) {
        if (linhaDigitavel == null || linhaDigitavel.length() != 47) {
            return null;
        }

        // Remove dígitos verificadores e reorganiza
        // Formato linha digitável: AAABC.CCCCX DDDDD.DDDDDY EEEEE.EEEEEZ K UUUUUUUUUUUUUU
        // Formato código barras:   AAABKUUUUUUUUUUUUUUCCCCCDDDDDDDDDEEEEEEEEEEE

        try {
            String campo1 = linhaDigitavel.substring(0, 4);  // AAAB (sem o 5º dígito que é verificador)
            String campo2 = linhaDigitavel.substring(5, 9);  // Parte de CCCCC
            String campo3 = linhaDigitavel.substring(10, 20); // DDDDD.DDDDD (sem último dígito verificador)
            String campo4 = linhaDigitavel.substring(21, 31); // EEEEE.EEEEE (sem último dígito verificador)
            String dv = linhaDigitavel.substring(32, 33);     // K (dígito verificador geral)
            String campo5 = linhaDigitavel.substring(33);     // UUUUUUUUUUUUUU (fator de vencimento + valor)

            // Reconstrói código de barras
            String codigoBarras = campo1 + dv + campo5 +
                    linhaDigitavel.substring(4, 5) + campo2 +
                    linhaDigitavel.substring(10, 15) + linhaDigitavel.substring(16, 21) +
                    linhaDigitavel.substring(21, 26) + linhaDigitavel.substring(27, 31);

            log.info("Linha digitável convertida para código de barras: {}", codigoBarras);
            return codigoBarras;

        } catch (Exception e) {
            log.error("Erro ao converter linha digitável para código de barras: {}", e.getMessage());
            return null;
        }
    }
}