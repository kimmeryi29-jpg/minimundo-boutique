package com.minimundo.logistica.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Movimiento de inventario inmutable: no existe ninguna operación para editarlo o eliminarlo (HU-56). */
@Document("movimientos")
public class Movimiento {

    @Id
    private String id;
    @Indexed
    private String sku;
    private TipoMovimiento tipo;
    private int cantidad;
    private int stockResultante;
    private String motivo;
    private String referencia;
    private String usuarioId;
    private String usuarioNombre;
    @Indexed
    private Instant fecha;

    public Movimiento() {
    }

    public Movimiento(String sku, TipoMovimiento tipo, int cantidad, int stockResultante, String motivo,
                      String referencia, String usuarioId, String usuarioNombre) {
        this.sku = sku;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.stockResultante = stockResultante;
        this.motivo = motivo;
        this.referencia = referencia;
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.fecha = Instant.now();
    }

    public String getId() { return id; }
    public String getSku() { return sku; }
    public TipoMovimiento getTipo() { return tipo; }
    public int getCantidad() { return cantidad; }
    public int getStockResultante() { return stockResultante; }
    public String getMotivo() { return motivo; }
    public String getReferencia() { return referencia; }
    public String getUsuarioId() { return usuarioId; }
    public String getUsuarioNombre() { return usuarioNombre; }
    public Instant getFecha() { return fecha; }
}
