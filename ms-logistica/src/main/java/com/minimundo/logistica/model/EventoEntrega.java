package com.minimundo.logistica.model;

import java.time.Instant;

/** Trazabilidad del reparto: asignación, salida, entrega e intentos fallidos (HU-106 a HU-110). */
public class EventoEntrega {

    public enum Tipo { ASIGNADO, EN_RUTA, ENTREGADO, FALLIDO }

    private Tipo tipo;
    private Instant fecha;
    private String usuarioId;
    private String usuarioNombre;
    private String motivo;
    private String detalle;

    public EventoEntrega() {
    }

    public EventoEntrega(Tipo tipo, String usuarioId, String usuarioNombre, String motivo, String detalle) {
        this.tipo = tipo;
        this.fecha = Instant.now();
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.motivo = motivo;
        this.detalle = detalle;
    }

    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}
