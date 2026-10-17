package com.minimundo.catalogo.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Excepción de negocio con el código HTTP que debe devolver el servicio (HU-102).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> campos;

    public ApiException(HttpStatus status, String mensaje) {
        this(status, mensaje, null);
    }

    public ApiException(HttpStatus status, String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.status = status;
        this.campos = campos;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getCampos() {
        return campos;
    }

    public static ApiException badRequest(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensaje);
    }

    public static ApiException badRequest(String mensaje, Map<String, String> campos) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensaje, campos);
    }

    public static ApiException unauthorized(String mensaje) {
        return new ApiException(HttpStatus.UNAUTHORIZED, mensaje);
    }

    public static ApiException forbidden(String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, mensaje);
    }

    public static ApiException notFound(String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, mensaje);
    }

    public static ApiException conflict(String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, mensaje);
    }

    public static ApiException unavailable(String mensaje) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
    }
}
