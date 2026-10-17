package com.minimundo.atencion.model;

import java.time.Instant;

/** Cambio de estado con fecha y usuario (HU-69, HU-70, HU-75). */
public class Historial {

    private String estado;
    private Instant fecha;
    private String usuarioId;
    private String usuarioNombre;
    private String detalle;

    public Historial() {
    }

    public Historial(String estado, String usuarioId, String usuarioNombre, String detalle) {
        this.estado = estado;
        this.fecha = Instant.now();
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.detalle = detalle;
    }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}
