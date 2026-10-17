package com.minimundo.logistica.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Inventario por SKU (db_logistica.inventario). El disponible siempre es stock − reservado y se guarda
 * para que la reserva sea una única actualización atómica condicionada (sección 5.4, HU-54).
 */
@Document("inventario")
public class Inventario {

    @Id
    private String id;
    @Indexed(unique = true)
    private String sku;
    private String productoNombre;
    private String talla;
    private String color;
    private int stock;
    private int reservado;
    private int disponible;
    /** Mínimo propio de la variante; si es null se usa el mínimo por defecto (HU-82). */
    private Integer stockMinimo;
    private Instant fechaActualizacion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }
    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public int getReservado() { return reservado; }
    public void setReservado(int reservado) { this.reservado = reservado; }
    public int getDisponible() { return disponible; }
    public void setDisponible(int disponible) { this.disponible = disponible; }
    public Integer getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Integer stockMinimo) { this.stockMinimo = stockMinimo; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(Instant fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
