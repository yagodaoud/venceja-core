package com.yagodaoud.venceja.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yagodaoud.venceja.dto.ApiResponse;
import com.yagodaoud.venceja.dto.BoletoRequest;
import com.yagodaoud.venceja.dto.BoletoResponse;
import com.yagodaoud.venceja.dto.PagedResult;
import com.yagodaoud.venceja.entity.BoletoStatus;
import com.yagodaoud.venceja.service.BoletoService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Controller para gerenciamento de boletos
 */
@Slf4j
@Path("/api/v1/boletos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class BoletoController {

    @Inject
    BoletoService boletoService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SecurityIdentity securityIdentity;

    @POST
    @Path("/scan")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response scanBoleto(
            @RestForm("file") FileUpload file,
            @RestForm("data") String dataJson) {
        try {
            String userEmail = securityIdentity.getPrincipal().getName();

            BoletoRequest request = null;
            if (dataJson != null && !dataJson.isEmpty()) {
                try {
                    request = objectMapper.readValue(dataJson, BoletoRequest.class);
                } catch (Exception e) {
                    log.warn("Erro ao parsear JSON de edição: {}", e.getMessage());
                }
            }

            if (request == null) {
                request = new BoletoRequest();
            }

            byte[] fileBytes = Files.readAllBytes(file.uploadedFile());

            CompletableFuture<BoletoResponse> future = boletoService.scanBoleto(
                    fileBytes,
                    file.fileName(),
                    request,
                    userEmail
            );
            BoletoResponse response = future.get();

            ApiResponse<BoletoResponse> apiResponse = ApiResponse.<BoletoResponse>builder()
                    .data(response)
                    .message("Boleto processado com sucesso")
                    .build();

            return Response.status(Response.Status.CREATED).entity(apiResponse).build();

        } catch (Exception e) {
            log.error("Erro ao processar boleto: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar boleto: " + e.getMessage(), e);
        }
    }

    @POST
    public Response createBoleto(
            @Valid BoletoRequest request) {
        try {
            String userEmail = securityIdentity.getPrincipal().getName();

            BoletoResponse response = boletoService.createBoleto(request, userEmail);

            ApiResponse<BoletoResponse> apiResponse = ApiResponse.<BoletoResponse>builder()
                    .data(response)
                    .message("Boleto criado com sucesso")
                    .build();

            return Response.status(Response.Status.CREATED).entity(apiResponse).build();

        } catch (Exception e) {
            log.error("Erro ao criar boleto: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao criar boleto: " + e.getMessage(), e);
        }
    }

    @GET
    public Response listBoletos(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("sortBy") @DefaultValue("id") String sortBy,
            @QueryParam("direction") @DefaultValue("desc") String direction,
            @QueryParam("status") String status, // Receives "PENDENTE,VENCIDO"
            @QueryParam("dataInicio") String dataInicioStr,
            @QueryParam("dataFim") String dataFimStr) {
        
        String userEmail = securityIdentity.getPrincipal().getName();

        LocalDate dataInicio = null;
        LocalDate dataFim = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        if (dataInicioStr != null) dataInicio = LocalDate.parse(dataInicioStr, formatter);
        if (dataFimStr != null) dataFim = LocalDate.parse(dataFimStr, formatter);

        List<BoletoStatus> statusList = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                statusList = Arrays.stream(status.split(","))
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .map(BoletoStatus::valueOf)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Status inválido: " + status + ". Valores aceitos: PENDENTE, PAGO, VENCIDO");
            }
        }

        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("Data inicial não pode ser posterior à data final");
        }

        PagedResult<BoletoResponse> result = boletoService.listBoletos(
                userEmail, statusList, dataInicio, dataFim, page, size, sortBy, direction);

        ApiResponse.Meta meta = ApiResponse.Meta.builder()
                .total(result.getTotalElements())
                .page(result.getPage())
                .size(result.getSize())
                .build();

        ApiResponse<List<BoletoResponse>> response = ApiResponse.<List<BoletoResponse>>builder()
                .data(result.getContent())
                .message("Boletos listados com sucesso")
                .meta(meta)
                .build();

        return Response.ok(response).build();
    }

    @PUT
    @Path("/{id}")
    public Response updateBoleto(
            @PathParam("id") Long id,
            @Valid BoletoRequest request) {
        try {
            String userEmail = securityIdentity.getPrincipal().getName();

            BoletoResponse response = boletoService.updateBoleto(id, request, userEmail);

            ApiResponse<BoletoResponse> apiResponse = ApiResponse.<BoletoResponse>builder()
                    .data(response)
                    .message("Boleto atualizado com sucesso")
                    .build();

            return Response.ok(apiResponse).build();

        } catch (Exception e) {
            log.error("Erro ao atualizar boleto: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao atualizar boleto: " + e.getMessage(), e);
        }
    }

    @PUT
    @Path("/{id}/pagar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response pagarBoleto(
            @PathParam("id") Long id,
            @RestForm("comprovante") FileUpload comprovante,
            @RestForm("semComprovante") @DefaultValue("false") Boolean semComprovante) {
        try {
            String userEmail = securityIdentity.getPrincipal().getName();

            byte[] comprovanteBytes = null;
            String comprovanteName = null;

            if (comprovante != null && comprovante.uploadedFile() != null) {
                comprovanteBytes = Files.readAllBytes(comprovante.uploadedFile());
                comprovanteName = comprovante.fileName();
            }

            BoletoResponse response = boletoService.pagarBoleto(
                    id, userEmail, comprovanteBytes, comprovanteName, semComprovante);

            ApiResponse<BoletoResponse> apiResponse = ApiResponse.<BoletoResponse>builder()
                    .data(response)
                    .message("Boleto marcado como pago")
                    .build();

            return Response.ok(apiResponse).build();

        } catch (Exception e) {
            log.error("Erro ao marcar boleto como pago: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao marcar boleto como pago: " + e.getMessage(), e);
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteBoleto(
            @PathParam("id") Long id) {
        try {
            String userEmail = securityIdentity.getPrincipal().getName();

            boletoService.deletarBoleto(id, userEmail);

            ApiResponse<Void> response = ApiResponse.<Void>builder()
                    .message("Boleto deletado com sucesso")
                    .build();

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Erro ao deletar boleto: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao deletar boleto: " + e.getMessage(), e);
        }
    }
}