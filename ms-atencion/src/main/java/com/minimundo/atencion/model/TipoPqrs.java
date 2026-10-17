package com.minimundo.atencion.model;

/** HU-61: tipos de PQRS. */
public enum TipoPqrs {
    PETICION("Petición"), QUEJA("Queja"), RECLAMO("Reclamo"), SUGERENCIA("Sugerencia");

    private final String texto;

    TipoPqrs(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
