package com.minimundo.atencion.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Documento PQRS (db_atencion.pqrs). */
@Document("pqrs")
public class Pqrs {

    @Id
    private String id;
    @Indexed(unique = true)
    private String radicado;
    private TipoPqrs tipo;
    private String descripcion;
    @Indexed
    private String clienteId;
    private String clienteNombre;
    private String clienteEmail;
    private String pedidoId;
    private String pedidoCodigo;
    @Indexed
    private EstadoPqrs estado;
    private String asesorId;
    private String asesorNombre;
    private String respuesta;
    @Indexed
    private Instant fechaRadicacion;
    private Instant fechaAsignacion;
    private Instant fechaCierre;
    private List<Historial> historial = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRadicado() { return radicado; }
    public void setRadicado(String radicado) { this.radicado = radicado; }
    public TipoPqrs getTipo() { return tipo; }
    public void setTipo(TipoPqrs tipo) { this.tipo = tipo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }
    public String getPedidoId() { return pedidoId; }
    public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }
    public String getPedidoCodigo() { return pedidoCodigo; }
    public void setPedidoCodigo(String pedidoCodigo) { this.pedidoCodigo = pedidoCodigo; }
    public EstadoPqrs getEstado() { return estado; }
    public void setEstado(EstadoPqrs estado) { this.estado = estado; }
    public String getAsesorId() { return asesorId; }
    public void setAsesorId(String asesorId) { this.asesorId = asesorId; }
    public String getAsesorNombre() { return asesorNombre; }
    public void setAsesorNombre(String asesorNombre) { this.asesorNombre = asesorNombre; }
    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public Instant getFechaRadicacion() { return fechaRadicacion; }
    public void setFechaRadicacion(Instant fechaRadicacion) { this.fechaRadicacion = fechaRadicacion; }
    public Instant getFechaAsignacion() { return fechaAsignacion; }
    public void setFechaAsignacion(Instant fechaAsignacion) { this.fechaAsignacion = fechaAsignacion; }
    public Instant getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(Instant fechaCierre) { this.fechaCierre = fechaCierre; }
    public List<Historial> getHistorial() { return historial; }
    public void setHistorial(List<Historial> historial) { this.historial = historial; }
}
