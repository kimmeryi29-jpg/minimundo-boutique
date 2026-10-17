package com.minimundo.pedidos.model;

public class ItemCarrito {

    private String sku;
    private String productoId;
    private String nombre;
    private String talla;
    private String color;
    private int cantidad;
    private String imagenId;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public String getImagenId() { return imagenId; }
    public void setImagenId(String imagenId) { this.imagenId = imagenId; }
}
