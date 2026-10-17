package com.minimundo.pedidos.model;

import java.time.Instant;

/** Registro del arreglo historialEstados: estado, fecha y usuario (sección 5.4, HU-87). */
public class CambioEstado {

    private EstadoPedido estado;
    private Instant fecha;
    private String usuarioId;
    private String usuarioNombre;
    private String detalle;

    public CambioEstado() {
    }

    public CambioEstado(EstadoPedido estado, Instant fecha, String usuarioId, String usuarioNombre, String detalle) {
        this.estado = estado;
        this.fecha = fecha;
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.detalle = detalle;
    }

    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}
