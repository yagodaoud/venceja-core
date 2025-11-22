package com.yagodaoud.venceja.controller;

import com.yagodaoud.venceja.dto.*;
import com.yagodaoud.venceja.entity.RefreshTokenEntity;
import com.yagodaoud.venceja.service.AuthService;
import com.yagodaoud.venceja.service.RefreshTokenService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller para autenticação com suporte a refresh tokens
 */
@Slf4j
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthController {

    @Inject
    AuthService authService;

    @Inject
    RefreshTokenService refreshTokenService;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Login endpoint - retorna access token e refresh token
     */
    @POST
    @Path("/login")
    public Response login(
            @Valid LoginRequest request,
            @HeaderParam("User-Agent") String userAgent,
            @HeaderParam("X-Device-Info") String deviceInfo) {

        log.info("Attempting login for user: {}", request.getEmail());

        // Usa device info customizado ou User-Agent como fallback
        String device = deviceInfo != null ? deviceInfo : userAgent;

        Map<String, Object> data = authService.login(request, device);

        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .data(data)
                .message("Login realizado com sucesso")
                .build();

        return Response.ok(response).build();
    }

    /**
     * Refresh token endpoint - gera novo access token
     */
    @POST
    @Path("/refresh")
    public Response refreshToken(
            @Valid RefreshTokenRequest request) {

        log.info("Refreshing access token");

        RefreshTokenResponse data = authService.refreshToken(request);

        ApiResponse<RefreshTokenResponse> response = ApiResponse.<RefreshTokenResponse>builder()
                .data(data)
                .message("Token renovado com sucesso")
                .build();

        return Response.ok(response).build();
    }

    /**
     * Logout endpoint - revoga o refresh token
     */
    @POST
    @Path("/logout")
    public Response logout(
            Map<String, String> body) {

        String refreshToken = body != null ? body.get("refreshToken") : null;

        if (refreshToken != null) {
            authService.logout(refreshToken);
            log.info("User logged out");
        }

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .message("Logout realizado com sucesso")
                .build();

        return Response.ok(response).build();
    }

    /**
     * Logout de todos os dispositivos
     */
    @Authenticated
    @POST
    @Path("/logout-all")
    public Response logoutAll() {
        String userEmail = securityIdentity.getPrincipal().getName();

        authService.logoutAllDevices(userEmail);
        log.info("User logged out from all devices: {}", userEmail);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .message("Logout realizado em todos os dispositivos")
                .build();

        return Response.ok(response).build();
    }

    /**
     * Lista sessões ativas do usuário
     */
    @Authenticated
    @GET
    @Path("/sessions")
    public Response getActiveSessions(
            @QueryParam("currentToken") String currentToken) {

        String userEmail = securityIdentity.getPrincipal().getName();

        List<RefreshTokenEntity> tokens = refreshTokenService.getActiveTokens(userEmail);

        List<ActiveSessionResponse> sessions = tokens.stream()
                .map(token -> ActiveSessionResponse.builder()
                        .id(token.getId())
                        .deviceInfo(token.getDeviceInfo())
                        .createdAt(token.getCreatedAt())
                        .expiresAt(token.getExpiresAt())
                        .current(token.getToken().equals(currentToken))
                        .build())
                .collect(Collectors.toList());

        ApiResponse<List<ActiveSessionResponse>> response = ApiResponse.<List<ActiveSessionResponse>>builder()
                .data(sessions)
                .message("Sessões ativas recuperadas com sucesso")
                .build();

        return Response.ok(response).build();
    }

    /**
     * Revoga uma sessão específica
     */
    @Authenticated
    @DELETE
    @Path("/sessions/{sessionId}")
    public Response revokeSession(
            @PathParam("sessionId") Long sessionId) {

        String userEmail = securityIdentity.getPrincipal().getName();

        // Verify ownership? The service or repository should probably handle this,
        // but here we filter by userEmail first.
        List<RefreshTokenEntity> tokens = refreshTokenService.getActiveTokens(userEmail);
        RefreshTokenEntity token = tokens.stream()
                .filter(t -> t.getId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada"));

        refreshTokenService.revokeRefreshToken(token.getToken());

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .message("Sessão revogada com sucesso")
                .build();

        return Response.ok(response).build();
    }
}