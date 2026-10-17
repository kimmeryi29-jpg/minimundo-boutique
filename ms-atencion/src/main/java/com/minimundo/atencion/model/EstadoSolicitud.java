package com.minimundo.atencion.model;

public enum EstadoSolicitud {
    PENDIENTE("Pendiente"), APROBADA("Aprobada"), RECHAZADA("Rechazada");

    private final String texto;

    EstadoSolicitud(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
