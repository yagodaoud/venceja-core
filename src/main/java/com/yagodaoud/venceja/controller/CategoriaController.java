package com.yagodaoud.venceja.controller;

import com.yagodaoud.venceja.dto.ApiResponse;
import com.yagodaoud.venceja.dto.CategoriaRequest;
import com.yagodaoud.venceja.dto.CategoriaResponse;
import com.yagodaoud.venceja.service.CategoriaService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller para gerenciamento de categorias
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/categorias")
@Authenticated
public class CategoriaController {

    @Inject
    CategoriaService categoriaService;

    @Inject
    SecurityIdentity securityIdentity;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoriaResponse>>> listCategorias(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        
        String userEmail = securityIdentity.getPrincipal().getName();

        Pageable pageable = PageRequest.of(page, size);

        Page<CategoriaResponse> categorias = categoriaService.listCategorias(userEmail, pageable);
        List<CategoriaResponse> categoriasList = new ArrayList<>(categorias.getContent());

        ApiResponse.Meta meta = ApiResponse.Meta.builder()
                .total(categorias.getTotalElements())
                .page(page)
                .size(size)
                .build();

        ApiResponse<List<CategoriaResponse>> response = ApiResponse.<List<CategoriaResponse>>builder()
                .data(categoriasList)
                .message("Categorias listadas com sucesso")
                .meta(meta)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoriaResponse>> createCategoria(
            @Valid @RequestBody CategoriaRequest request) {
        
        String userEmail = securityIdentity.getPrincipal().getName();

        CategoriaResponse categoria = categoriaService.createCategoria(request, userEmail);

        ApiResponse<CategoriaResponse> response = ApiResponse.<CategoriaResponse>builder()
                .data(categoria)
                .message("Categoria criada com sucesso")
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoriaResponse>> updateCategoria(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaRequest request) {
        
        String userEmail = securityIdentity.getPrincipal().getName();

        CategoriaResponse categoria = categoriaService.updateCategoria(id, request, userEmail);

        ApiResponse<CategoriaResponse> response = ApiResponse.<CategoriaResponse>builder()
                .data(categoria)
                .message("Categoria atualizada com sucesso")
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategoria(
            @PathVariable Long id) {
        
        String userEmail = securityIdentity.getPrincipal().getName();

        categoriaService.deleteCategoria(id, userEmail);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .message("Categoria deletada com sucesso")
                .build();

        return ResponseEntity.ok(response);
    }
}
