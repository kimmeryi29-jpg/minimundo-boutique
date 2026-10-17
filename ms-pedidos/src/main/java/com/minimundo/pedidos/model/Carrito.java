package com.minimundo.pedidos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Carrito del cliente (db_pedidos.carritos): uno por cliente, se conserva si el pago falla (HU-40). */
@Document("carritos")
public class Carrito {

    @Id
    private String id;
    @Indexed(unique = true)
    private String clienteId;
    private List<ItemCarrito> items = new ArrayList<>();
    private Instant fechaActualizacion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public List<ItemCarrito> getItems() { return items; }
    public void setItems(List<ItemCarrito> items) { this.items = items; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(Instant fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
