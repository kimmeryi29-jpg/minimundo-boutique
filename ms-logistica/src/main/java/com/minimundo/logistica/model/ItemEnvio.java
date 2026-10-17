package com.minimundo.logistica.model;

/** Ítem del pedido que el operario verifica durante el alistamiento (HU-52). */
public class ItemEnvio {

    private String sku;
    private String nombre;
    private String talla;
    private String color;
    private int cantidad;
    private boolean verificado;

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
    public boolean isVerificado() { return verificado; }
    public void setVerificado(boolean verificado) { this.verificado = verificado; }
}
