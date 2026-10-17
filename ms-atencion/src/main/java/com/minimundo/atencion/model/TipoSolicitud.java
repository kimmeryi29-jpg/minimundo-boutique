package com.minimundo.atencion.model;

public enum TipoSolicitud {
    CAMBIO("Cambio de talla"), DEVOLUCION("Devolución");

    private final String texto;

    TipoSolicitud(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
