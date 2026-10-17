package com.minimundo.logistica.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimundo.logistica.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respuestas 401 y 403 con el mismo formato de error que el resto del servicio.
 */
@Component
public class RestSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public RestSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        escribir(res, req, 401, "Unauthorized", "Debes iniciar sesión o tu sesión expiró");
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex)
            throws IOException {
        escribir(res, req, 403, "Forbidden", "No tienes permiso para realizar esta operación");
    }

    private void escribir(HttpServletResponse res, HttpServletRequest req, int codigo, String error, String mensaje)
            throws IOException {
        res.setStatus(codigo);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(res.getOutputStream(), ApiError.of(codigo, error, mensaje, req.getRequestURI(), null));
    }
}
