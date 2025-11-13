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

    // Padrões para extração de valor
    private static final Pattern VALOR_LABEL_PATTERN = Pattern.compile(
            "(?:VALOR\\s+DO\\s+DOCUMENTO|VALOR\\s+DOCUMENTO|\\(=\\)\\s*VALOR\\s+DO\\s+DOCUMENTO)[:\\s]*R?\\$?\\s*(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern VALOR_GENERICO_PATTERN = Pattern.compile(
            "R\\$?\\s*(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})",
            Pattern.CASE_INSENSITIVE
    );

    // Padrões para extração de vencimento (aceita /, . ou -)
    private static final Pattern VENCIMENTO_LABEL_PATTERN = Pattern.compile(
            "VENCIMENTO[:\\s]*(\\d{2}[/.\\-]\\d{2}[/.\\-]\\d{4})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATA_PATTERN = Pattern.compile(
            "\\b(\\d{2}[/.\\-]\\d{2}[/.\\-]\\d{4})\\b"
    );

    // Padrão para extração de fornecedor (mais flexível)
    private static final Pattern BENEFICIARIO_PATTERN = Pattern.compile(
            "BENEFICI[AÁ]RIO[:\\s]*\\n\\s*([^\\n]{5,100})",
            Pattern.CASE_INSENSITIVE
    );

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
     * Extrai valor do texto OCR com múltiplas estratégias
     */
    public BigDecimal extractValor(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }

        // Estratégia 1: Procura por "VALOR DO DOCUMENTO" seguido do valor
        Matcher labelMatcher = VALOR_LABEL_PATTERN.matcher(ocrText);
        if (labelMatcher.find()) {
            String valorStr = labelMatcher.group(1);
            BigDecimal valor = parseValor(valorStr);
            if (valor != null && isValorValido(valor)) {
                log.info("Valor encontrado via label 'VALOR DO DOCUMENTO': R$ {}", valor);
                return valor;
            }
        }

        // Estratégia 2: Procura todos os valores R$ e escolhe o mais provável
        List<BigDecimal> valoresEncontrados = new ArrayList<>();
        Matcher genericMatcher = VALOR_GENERICO_PATTERN.matcher(ocrText);

        while (genericMatcher.find()) {
            String valorStr = genericMatcher.group(1);
            BigDecimal valor = parseValor(valorStr);
            if (valor != null && isValorValido(valor)) {
                valoresEncontrados.add(valor);
            }
        }

        // Se encontrou valores, retorna o primeiro valor válido (normalmente é o principal)
        if (!valoresEncontrados.isEmpty()) {
            // Filtra valores muito pequenos (provavelmente taxas) e muito grandes (provavelmente erros)
            BigDecimal valorSelecionado = valoresEncontrados.stream()
                    .filter(v -> v.compareTo(new BigDecimal("10.00")) >= 0) // Mínimo R$ 10
                    .filter(v -> v.compareTo(new BigDecimal("100000.00")) <= 0) // Máximo R$ 100.000
                    .findFirst()
                    .orElse(valoresEncontrados.get(0));

            log.info("Valor encontrado via busca genérica: R$ {}", valorSelecionado);
            return valorSelecionado;
        }

        log.warn("Nenhum valor válido encontrado no OCR");
        return null;
    }

    /**
     * Converte string de valor para BigDecimal
     */
    private BigDecimal parseValor(String valorStr) {
        if (valorStr == null || valorStr.isEmpty()) {
            return null;
        }

        try {
            // Normaliza: remove pontos de milhar e troca vírgula por ponto
            String normalized = valorStr
                    .replaceAll("\\.", "")  // Remove pontos de milhar
                    .replace(",", ".");     // Troca vírgula decimal por ponto

            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            log.debug("Erro ao converter valor: {}", valorStr);
            return null;
        }
    }

    /**
     * Valida se o valor está em um range aceitável
     */
    private boolean isValorValido(BigDecimal valor) {
        return valor != null &&
                valor.compareTo(BigDecimal.ZERO) > 0 &&
                valor.compareTo(new BigDecimal("1000000.00")) < 0;
    }

    /**
     * Extrai data de vencimento do texto OCR com múltiplas estratégias
     */
    public LocalDate extractVencimento(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }

        // Estratégia 1: Procura por "VENCIMENTO" seguido da data
        Matcher labelMatcher = VENCIMENTO_LABEL_PATTERN.matcher(ocrText);
        if (labelMatcher.find()) {
            String dateStr = labelMatcher.group(1);
            LocalDate date = parseData(dateStr);
            if (date != null && isDataVencimentoValida(date)) {
                log.info("Vencimento encontrado via label: {}", date);
                return date;
            }
        }

        // Estratégia 2: Procura todas as datas e escolhe a mais provável
        List<LocalDate> datasEncontradas = new ArrayList<>();
        Matcher dateMatcher = DATA_PATTERN.matcher(ocrText);

        while (dateMatcher.find()) {
            String dateStr = dateMatcher.group(1);
            LocalDate date = parseData(dateStr);
            if (date != null && isDataVencimentoValida(date)) {
                datasEncontradas.add(date);
            }
        }

        // Retorna a primeira data futura encontrada (provavelmente o vencimento)
        LocalDate hoje = LocalDate.now();
        LocalDate dataFutura = datasEncontradas.stream()
                .filter(d -> d.isAfter(hoje.minusDays(1))) // Aceita hoje ou futuro
                .findFirst()
                .orElse(null);

        if (dataFutura != null) {
            log.info("Vencimento encontrado via busca genérica: {}", dataFutura);
            return dataFutura;
        }

        // Se não encontrou data futura, pega a mais recente
        if (!datasEncontradas.isEmpty()) {
            LocalDate dataMaisRecente = datasEncontradas.stream()
                    .max(LocalDate::compareTo)
                    .orElse(null);
            log.info("Vencimento encontrado (data mais recente): {}", dataMaisRecente);
            return dataMaisRecente;
        }

        log.warn("Nenhuma data de vencimento válida encontrada no OCR");
        return null;
    }

    /**
     * Converte string de data para LocalDate, tentando múltiplos formatos
     */
    private LocalDate parseData(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        // Normaliza: detecta se tem ano com 2 dígitos e converte para 4
        String normalized = dateStr;
        if (dateStr.matches("\\d{2}[/.\\-]\\d{2}[/.\\-]\\d{2}$")) {
            // Ano com 2 dígitos - assume 20XX
            String[] parts = dateStr.split("[/.\\-]");
            int ano = Integer.parseInt(parts[2]);
            // Se ano < 50, assume 20XX, senão 19XX
            int anoCompleto = ano < 50 ? 2000 + ano : 1900 + ano;
            normalized = parts[0] + dateStr.charAt(2) + parts[1] + dateStr.charAt(2) + anoCompleto;
        }

        // Lista de formatos suportados
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy")
        };

        // Tenta cada formato disponível
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException e) {
                // Tenta o próximo formato
            }
        }

        log.debug("Erro ao converter data com todos os formatos: {} (normalizada: {})", dateStr, normalized);
        return null;
    }

    /**
     * Valida se a data de vencimento está em um range aceitável
     */
    private boolean isDataVencimentoValida(LocalDate date) {
        if (date == null) {
            return false;
        }

        LocalDate hoje = LocalDate.now();
        LocalDate limitePassado = hoje.minusYears(1);  // Até 1 ano no passado
        LocalDate limiteFuturo = hoje.plusYears(2);    // Até 2 anos no futuro

        return date.isAfter(limitePassado) && date.isBefore(limiteFuturo);
    }

    /**
     * Extrai fornecedor/beneficiário do texto OCR com múltiplas estratégias
     */
    public String extractFornecedor(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return "Fornecedor não identificado";
        }

        // Estratégia 1: Procura por "BENEFICIÁRIO" seguido do nome
        Matcher beneficiarioMatcher = BENEFICIARIO_PATTERN.matcher(ocrText);
        if (beneficiarioMatcher.find()) {
            String fornecedor = beneficiarioMatcher.group(1).trim();
            fornecedor = cleanFornecedor(fornecedor);
            if (isFornecedorValido(fornecedor)) {
                log.info("Fornecedor encontrado via label 'BENEFICIÁRIO': {}", fornecedor);
                return fornecedor;
            }
        }

        // Estratégia 2: Primeira linha significativa
        String[] lines = ocrText.split("\\n");
        for (String line : lines) {
            line = line.trim();

            // Ignora linhas muito curtas, números, ou palavras-chave comuns
            if (line.length() < 5 || line.length() > 100) continue;
            if (line.matches(".*\\d{4,}.*")) continue; // Ignora linhas com muitos números
            if (line.matches("(?i).*(RECIBO|PAGADOR|DOCUMENTO|LOCAL|VENCIMENTO|AGENCIA).*")) continue;

            // Verifica se é um nome válido (começa com letra)
            if (line.matches("[A-ZÁÀÂÃÉÈÊÍÏÓÔÕÖÚÇÑa-záàâãéèêíïóôõöúçñ].*")) {
                String fornecedor = cleanFornecedor(line);
                if (isFornecedorValido(fornecedor)) {
                    log.info("Fornecedor encontrado via primeira linha: {}", fornecedor);
                    return fornecedor;
                }
            }
        }

        // Estratégia 3: Procura sequência de palavras em maiúscula
        Pattern upperCasePattern = Pattern.compile("\\b[A-ZÁÉÍÓÚÇÑ][A-ZÁÉÍÓÚÇÑ\\s&.-]{10,80}\\b");
        Matcher upperCaseMatcher = upperCasePattern.matcher(ocrText);

        while (upperCaseMatcher.find()) {
            String candidato = upperCaseMatcher.group().trim();
            candidato = cleanFornecedor(candidato);

            if (isFornecedorValido(candidato)) {
                log.info("Fornecedor encontrado via maiúsculas: {}", candidato);
                return candidato;
            }
        }

        log.warn("Não foi possível identificar o fornecedor no OCR");
        return "Fornecedor não identificado";
    }

    /**
     * Limpa e normaliza o nome do fornecedor
     */
    private String cleanFornecedor(String fornecedor) {
        if (fornecedor == null) {
            return "";
        }

        return fornecedor
                .trim()
                .replaceAll("\\s+", " ")  // Remove espaços múltiplos
                .replaceAll("^[-\\s.]+|[-\\s.]+$", ""); // Remove pontos/hífens no início/fim
    }

    /**
     * Valida se o fornecedor extraído é aceitável
     */
    private boolean isFornecedorValido(String fornecedor) {
        if (fornecedor == null || fornecedor.length() < 5) {
            return false;
        }

        // Rejeita se for só números ou caracteres especiais
        if (fornecedor.matches("^[\\d\\s.,-]+$")) {
            return false;
        }

        // Rejeita palavras-chave conhecidas (com a lista atualizada)
        String upper = fornecedor.toUpperCase();
        String[] palavrasInvalidas = {
                // Palavras do boleto que não são o fornecedor
                "AUTENTICAÇÃO", "AUTENTICACAO", "RECIBO DO SACADO",

                // Palavras de labels comuns
                "BENEFICIARIO", "PAGADOR", "SACADO", "ENDERECO",
                "LOCAL DE PAGAMENTO", "VENCIMENTO", "AGENCIA",
                "CODIGO", "NUMERO", "DOCUMENTO", "DATA", "VALOR"
        };

        for (String palavra : palavrasInvalidas) {
            if (upper.equals(palavra)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extrai código de barras ou linha digitável do texto OCR
     */
    public String extractCodigoBarras(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return null;
        }

        // Remove quebras de linha para facilitar matching
        String cleanText = ocrText.replaceAll("\\r?\\n", " ");

        // 1. Tenta encontrar linha digitável (47 dígitos com separadores)
        Pattern linhaDigitavelPattern = Pattern.compile(
                "\\b(\\d{5}\\.\\d{5})\\s+(\\d{5}\\.\\d{6})\\s+(\\d{5}\\.\\d{6})\\s+(\\d)\\s+(\\d{14})\\b"
        );
        Matcher linhaDigitavelMatcher = linhaDigitavelPattern.matcher(cleanText);

        if (linhaDigitavelMatcher.find()) {
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

        log.warn("Nenhum código de barras ou linha digitável encontrado no texto OCR");
        return null;
    }
}