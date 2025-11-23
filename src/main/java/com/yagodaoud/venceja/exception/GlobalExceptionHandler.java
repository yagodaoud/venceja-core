package com.yagodaoud.venceja.exception;

import com.yagodaoud.venceja.dto.ErrorResponse;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler global de exceções da API
 */
@Slf4j
@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable ex) {
        log.error("Erro capturado: {}", ex.getMessage(), ex);

        if (ex instanceof IllegalArgumentException) {
            return handleIllegalArgumentException((IllegalArgumentException) ex);
        }

        // Add other specific exception handling here if needed
        // Note: Bean Validation errors are usually handled by Quarkus automatically (ResteasyViolationException),
        // but we can override if we want custom format. For now let's handle generic ones.

        return handleGenericException(ex);
    }

    private Response handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse.ErrorDetail errorDetail = ErrorResponse.ErrorDetail.builder()
                .code("INVALID_REQUEST")
                .message(ex.getMessage())
                .details(List.of())
                .build();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(errorDetail)
                .build();

        return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse).build();
    }

    private Response handleGenericException(Throwable ex) {
        String code = "INTERNAL_ERROR";
        String message = "Erro interno do servidor";
        Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;

        if (ex.getMessage() != null && ex.getMessage().contains("OCR")) {
            code = "OCR_FAIL";
            message = "Falha ao processar OCR. Por favor, tente novamente ou insira os dados manualmente.";
            status = Response.Status.BAD_REQUEST;
        }

        ErrorResponse.ErrorDetail errorDetail = ErrorResponse.ErrorDetail.builder()
                .code(code)
                .message(message)
                .details(List.of(ex.getMessage() != null ? ex.getMessage() : "Erro desconhecido"))
                .build();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(errorDetail)
                .build();

        return Response.status(status).entity(errorResponse).build();
    }
}
