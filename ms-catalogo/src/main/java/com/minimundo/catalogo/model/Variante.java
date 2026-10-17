package com.minimundo.catalogo.model;

/** Combinación de talla y color de un producto, identificada por un SKU único (HU-17, HU-25). */
public class Variante {

    private String sku;
    private String talla;
    private String color;

    public Variante() {
    }

    public Variante(String sku, String talla, String color) {
        this.sku = sku;
        this.talla = talla;
        this.color = color;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
