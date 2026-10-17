package com.minimundo.logistica.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Reserva de stock de un pedido: ACTIVA al confirmar, CONSUMIDA al despachar y LIBERADA al cancelar o fallar. */
@Document("reservas")
public class Reserva {

    public enum Estado { ACTIVA, CONSUMIDA, LIBERADA }

    @Id
    private String id;
    @Indexed(unique = true)
    private String pedidoId;
    private String pedidoCodigo;
    private List<ItemReserva> items = new ArrayList<>();
    private Estado estado = Estado.ACTIVA;
    private Instant fecha;
    private Instant fechaCierre;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPedidoId() { return pedidoId; }
    public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }
    public String getPedidoCodigo() { return pedidoCodigo; }
    public void setPedidoCodigo(String pedidoCodigo) { this.pedidoCodigo = pedidoCodigo; }
    public List<ItemReserva> getItems() { return items; }
    public void setItems(List<ItemReserva> items) { this.items = items; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
    public Instant getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(Instant fechaCierre) { this.fechaCierre = fechaCierre; }
}
