package com.minimundo.web.api;

import java.util.Map;

/** Error devuelto por el api-gateway o por un microservicio, con su mensaje en español. */
public class ApiException extends RuntimeException {

    private final int status;
    private final Map<String, String> campos;

    public ApiException(int status, String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.status = status;
        this.campos = campos == null ? Map.of() : campos;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, String> getCampos() {
        return campos;
    }

    public boolean noDisponible() {
        return status >= 500;
    }
}
