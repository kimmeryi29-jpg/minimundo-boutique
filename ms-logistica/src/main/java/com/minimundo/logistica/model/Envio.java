package com.minimundo.logistica.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Envío de un pedido (db_logistica.envios): alistamiento, despacho con guía, asignación de repartidor y entrega.
 * Se crea cuando el operario toma el pedido pagado de la cola (HU-51).
 */
@Document("envios")
public class Envio {

    @Id
    private String id;
    /** Un pedido solo puede tener un envío: dos operarios no pueden tomar el mismo pedido (HU-51). */
    @Indexed(unique = true)
    private String pedidoId;
    private String pedidoCodigo;
    private String clienteId;
    private String clienteNombre;
    private String clienteEmail;
    private DireccionEnvio direccion;
    private List<ItemEnvio> items = new ArrayList<>();
    @Indexed
    private EstadoEnvio estado;
    private Instant fechaPago;
    private String operarioId;
    private String operarioNombre;
    private Instant fechaToma;
    private boolean alistamientoConfirmado;
    private Instant fechaAlistamiento;
    private String transportadora;
    private String guia;
    private Instant fechaDespacho;
    @Indexed
    private String repartidorId;
    private String repartidorNombre;
    private Instant fechaAsignacion;
    private String asignadoPor;
    private Instant fechaEnRuta;
    private Instant fechaEntrega;
    private boolean entregaFallida;
    private String ultimoMotivoFallido;
    private List<EventoEntrega> eventos = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPedidoId() { return pedidoId; }
    public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }
    public String getPedidoCodigo() { return pedidoCodigo; }
    public void setPedidoCodigo(String pedidoCodigo) { this.pedidoCodigo = pedidoCodigo; }
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }
    public DireccionEnvio getDireccion() { return direccion; }
    public void setDireccion(DireccionEnvio direccion) { this.direccion = direccion; }
    public List<ItemEnvio> getItems() { return items; }
    public void setItems(List<ItemEnvio> items) { this.items = items; }
    public EstadoEnvio getEstado() { return estado; }
    public void setEstado(EstadoEnvio estado) { this.estado = estado; }
    public Instant getFechaPago() { return fechaPago; }
    public void setFechaPago(Instant fechaPago) { this.fechaPago = fechaPago; }
    public String getOperarioId() { return operarioId; }
    public void setOperarioId(String operarioId) { this.operarioId = operarioId; }
    public String getOperarioNombre() { return operarioNombre; }
    public void setOperarioNombre(String operarioNombre) { this.operarioNombre = operarioNombre; }
    public Instant getFechaToma() { return fechaToma; }
    public void setFechaToma(Instant fechaToma) { this.fechaToma = fechaToma; }
    public boolean isAlistamientoConfirmado() { return alistamientoConfirmado; }
    public void setAlistamientoConfirmado(boolean alistamientoConfirmado) { this.alistamientoConfirmado = alistamientoConfirmado; }
    public Instant getFechaAlistamiento() { return fechaAlistamiento; }
    public void setFechaAlistamiento(Instant fechaAlistamiento) { this.fechaAlistamiento = fechaAlistamiento; }
    public String getTransportadora() { return transportadora; }
    public void setTransportadora(String transportadora) { this.transportadora = transportadora; }
    public String getGuia() { return guia; }
    public void setGuia(String guia) { this.guia = guia; }
    public Instant getFechaDespacho() { return fechaDespacho; }
    public void setFechaDespacho(Instant fechaDespacho) { this.fechaDespacho = fechaDespacho; }
    public String getRepartidorId() { return repartidorId; }
    public void setRepartidorId(String repartidorId) { this.repartidorId = repartidorId; }
    public String getRepartidorNombre() { return repartidorNombre; }
    public void setRepartidorNombre(String repartidorNombre) { this.repartidorNombre = repartidorNombre; }
    public Instant getFechaAsignacion() { return fechaAsignacion; }
    public void setFechaAsignacion(Instant fechaAsignacion) { this.fechaAsignacion = fechaAsignacion; }
    public String getAsignadoPor() { return asignadoPor; }
    public void setAsignadoPor(String asignadoPor) { this.asignadoPor = asignadoPor; }
    public Instant getFechaEnRuta() { return fechaEnRuta; }
    public void setFechaEnRuta(Instant fechaEnRuta) { this.fechaEnRuta = fechaEnRuta; }
    public Instant getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(Instant fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public boolean isEntregaFallida() { return entregaFallida; }
    public void setEntregaFallida(boolean entregaFallida) { this.entregaFallida = entregaFallida; }
    public String getUltimoMotivoFallido() { return ultimoMotivoFallido; }
    public void setUltimoMotivoFallido(String ultimoMotivoFallido) { this.ultimoMotivoFallido = ultimoMotivoFallido; }
    public List<EventoEntrega> getEventos() { return eventos; }
    public void setEventos(List<EventoEntrega> eventos) { this.eventos = eventos; }
}
