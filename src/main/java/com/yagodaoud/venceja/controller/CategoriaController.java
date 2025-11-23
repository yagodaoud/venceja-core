package com.yagodaoud.venceja.controller;

import com.yagodaoud.venceja.dto.ApiResponse;
import com.yagodaoud.venceja.dto.CategoriaRequest;
import com.yagodaoud.venceja.dto.CategoriaResponse;
import com.yagodaoud.venceja.dto.PagedResult;
import com.yagodaoud.venceja.service.CategoriaService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Controller para gerenciamento de categorias
 */
@Slf4j
@Path("/api/v1/categorias")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class CategoriaController {

    @Inject
    CategoriaService categoriaService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public Response listCategorias(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        
        String userEmail = securityIdentity.getPrincipal().getName();
        
        PagedResult<CategoriaResponse> result = categoriaService.listCategorias(userEmail, page, size);
        
        ApiResponse.Meta meta = ApiResponse.Meta.builder()
                .total(result.getTotalElements())
                .page(result.getPage())
                .size(result.getSize())
                .build();
        
        ApiResponse<List<CategoriaResponse>> response = ApiResponse.<List<CategoriaResponse>>builder()
                .data(result.getContent())
                .message("Categorias listadas com sucesso")
                .meta(meta)
                .build();
                
        return Response.ok(response).build();
    }

    @POST
    public Response createCategoria(
            @Valid CategoriaRequest request) {
        
        String userEmail = securityIdentity.getPrincipal().getName();

        CategoriaResponse categoria = categoriaService.createCategoria(request, userEmail);

        ApiResponse<CategoriaResponse> response = ApiResponse.<CategoriaResponse>builder()
                .data(categoria)
                .message("Categoria criada com sucesso")
                .build();

        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    public Response updateCategoria(
            @PathParam("id") Long id,
            @Valid CategoriaRequest request) {
        
        String userEmail = securityIdentity.getPrincipal().getName();

        CategoriaResponse categoria = categoriaService.updateCategoria(id, request, userEmail);

        ApiResponse<CategoriaResponse> response = ApiResponse.<CategoriaResponse>builder()
                .data(categoria)
                .message("Categoria atualizada com sucesso")
                .build();

        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteCategoria(
            @PathParam("id") Long id) {
        
        String userEmail = securityIdentity.getPrincipal().getName();

        categoriaService.deleteCategoria(id, userEmail);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .message("Categoria deletada com sucesso")
                .build();

        return Response.ok(response).build();
    }
}
