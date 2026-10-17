package com.minimundo.atencion.model;

/** HU-63: RADICADA → EN_GESTION → CERRADA. */
public enum EstadoPqrs {
    RADICADA("Radicada"), EN_GESTION("En gestión"), CERRADA("Cerrada");

    private final String texto;

    EstadoPqrs(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
