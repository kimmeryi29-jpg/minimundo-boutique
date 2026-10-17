package com.minimundo.pedidos.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Formato único de error para todos los servicios (HU-102): código, mensaje y fecha.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int codigo, String error, String mensaje, String fecha, String ruta, Map<String, String> campos) {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public static ApiError of(int codigo, String error, String mensaje, String ruta, Map<String, String> campos) {
        return new ApiError(codigo, error, mensaje, OffsetDateTime.now(ZONA).toString(), ruta,
                campos == null || campos.isEmpty() ? null : campos);
    }
}
