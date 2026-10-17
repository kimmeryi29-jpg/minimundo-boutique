package com.minimundo.atencion.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Solicitud de cambio de talla o devolución (db_atencion.solicitudes), HU-64 a HU-67, HU-72, HU-73. */
@Document("solicitudes")
public class Solicitud {

    @Id
    private String id;
    @Indexed(unique = true)
    private String numero;
    private TipoSolicitud tipo;
    @Indexed
    private String clienteId;
    private String clienteNombre;
    private String pedidoId;
    private String pedidoCodigo;
    private String sku;
    private String productoId;
    private String nombreProducto;
    private String talla;
    private String color;
    private int cantidad;
    private String tallaDeseada;
    private String skuDeseado;
    private String motivo;
    @Indexed
    private EstadoSolicitud estado;
    private String motivoRechazo;
    private String asesorId;
    private String asesorNombre;
    private Instant fechaSolicitud;
    private Instant fechaDecision;
    private boolean reingresada;
    private Instant fechaReingreso;
    private String reingresadaPor;
    private List<Historial> historial = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public TipoSolicitud getTipo() { return tipo; }
    public void setTipo(TipoSolicitud tipo) { this.tipo = tipo; }
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getPedidoId() { return pedidoId; }
    public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }
    public String getPedidoCodigo() { return pedidoCodigo; }
    public void setPedidoCodigo(String pedidoCodigo) { this.pedidoCodigo = pedidoCodigo; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }
    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }
    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public String getTallaDeseada() { return tallaDeseada; }
    public void setTallaDeseada(String tallaDeseada) { this.tallaDeseada = tallaDeseada; }
    public String getSkuDeseado() { return skuDeseado; }
    public void setSkuDeseado(String skuDeseado) { this.skuDeseado = skuDeseado; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public EstadoSolicitud getEstado() { return estado; }
    public void setEstado(EstadoSolicitud estado) { this.estado = estado; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }
    public String getAsesorId() { return asesorId; }
    public void setAsesorId(String asesorId) { this.asesorId = asesorId; }
    public String getAsesorNombre() { return asesorNombre; }
    public void setAsesorNombre(String asesorNombre) { this.asesorNombre = asesorNombre; }
    public Instant getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(Instant fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
    public Instant getFechaDecision() { return fechaDecision; }
    public void setFechaDecision(Instant fechaDecision) { this.fechaDecision = fechaDecision; }
    public boolean isReingresada() { return reingresada; }
    public void setReingresada(boolean reingresada) { this.reingresada = reingresada; }
    public Instant getFechaReingreso() { return fechaReingreso; }
    public void setFechaReingreso(Instant fechaReingreso) { this.fechaReingreso = fechaReingreso; }
    public String getReingresadaPor() { return reingresadaPor; }
    public void setReingresadaPor(String reingresadaPor) { this.reingresadaPor = reingresadaPor; }
    public List<Historial> getHistorial() { return historial; }
    public void setHistorial(List<Historial> historial) { this.historial = historial; }
}
