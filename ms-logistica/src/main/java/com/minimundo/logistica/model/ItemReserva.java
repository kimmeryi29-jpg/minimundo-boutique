package com.minimundo.logistica.model;

public class ItemReserva {

    private String sku;
    private String nombre;
    private String talla;
    private String color;
    private int cantidad;

    public ItemReserva() {
    }

    public ItemReserva(String sku, String nombre, String talla, String color, int cantidad) {
        this.sku = sku;
        this.nombre = nombre;
        this.talla = talla;
        this.color = color;
        this.cantidad = cantidad;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
