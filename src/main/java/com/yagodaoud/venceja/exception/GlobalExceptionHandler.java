package com.yagodaoud.venceja.exception;

import com.yagodaoud.venceja.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler global de exceções da API
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
                List<String> details = new ArrayList<>();

                ex.getBindingResult().getAllErrors().forEach(error -> {
                        String fieldName = ((FieldError) error).getField();
                        String errorMessage = error.getDefaultMessage();
                        details.add(fieldName + ": " + errorMessage);
                });

                ErrorResponse.ErrorDetail errorDetail = ErrorResponse.ErrorDetail.builder()
                                .code("VALIDATION_ERROR")
                                .message("Erro de validação nos dados fornecidos")
                                .details(details)
                                .build();

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .error(errorDetail)
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        @ExceptionHandler(UsernameNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleUsernameNotFoundException(UsernameNotFoundException ex) {
                ErrorResponse.ErrorDetail errorDetail = ErrorResponse.ErrorDetail.builder()
                                .code("USER_NOT_FOUND")
                                .message("Usuário não encontrado")
                                .details(List.of(ex.getMessage()))
                                .build();

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .error(errorDetail)
                                .build();

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
                ErrorResponse.ErrorDetail errorDetail = ErrorResponse.ErrorDetail.builder()
                                .code("INVALID_CREDENTIALS")
                                .message("Credenciais inválidas")
                                .details(List.of("Email ou senha incorretos"))
                                .build();

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .error(errorDetail)
                                .build();

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
                ErrorResponse.ErrorDetail errorDetail = ErrorResponse.ErrorDetail.builder()
                                .code("INVALID_REQUEST")
                                .message(ex.getMessage())
                                .details(List.of())
                                .build();

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .error(errorDetail)
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
                log.error("Erro não tratado: {}", ex.getMessage(), ex);

                String code = "INTERNAL_ERROR";
                String message = "Erro interno do servidor";

                if (ex.getMessage() != null && ex.getMessage().contains("OCR")) {
                        code = "OCR_FAIL";
                        message = "Falha ao processar OCR. Por favor, tente novamente ou insira os dados manualmente.";
                }

                ErrorResponse.ErrorDetail errorDetail = ErrorResponse.ErrorDetail.builder()
                                .code(code)
                                .message(message)
                                .details(List.of(ex.getMessage() != null ? ex.getMessage() : "Erro desconhecido"))
                                .build();

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .error(errorDetail)
                                .build();

                HttpStatus status = code.equals("OCR_FAIL") ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;

                return ResponseEntity.status(status).body(errorResponse);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
                log.error("Erro genérico: {}", ex.getMessage(), ex);

                ErrorResponse.ErrorDetail errorDetail = ErrorResponse.ErrorDetail.builder()
                                .code("INTERNAL_ERROR")
                                .message("Erro interno do servidor")
                                .details(List.of("Ocorreu um erro inesperado. Tente novamente mais tarde."))
                                .build();

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .error(errorDetail)
                                .build();

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
}
