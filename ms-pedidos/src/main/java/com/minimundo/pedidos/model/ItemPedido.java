package com.minimundo.pedidos.model;

/** Copia (snapshot) del producto al momento de la compra: nombre, talla, color y precio (sección 5.3). */
public class ItemPedido {

    private String sku;
    private String productoId;
    private String nombre;
    private String talla;
    private String color;
    private int cantidad;
    private long precioOriginal;
    private long precioUnitario;
    private int descuentoPorcentaje;
    private String imagenId;

    public long subtotal() {
        return precioUnitario * cantidad;
    }

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
    public long getPrecioOriginal() { return precioOriginal; }
    public void setPrecioOriginal(long precioOriginal) { this.precioOriginal = precioOriginal; }
    public long getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(long precioUnitario) { this.precioUnitario = precioUnitario; }
    public int getDescuentoPorcentaje() { return descuentoPorcentaje; }
    public void setDescuentoPorcentaje(int descuentoPorcentaje) { this.descuentoPorcentaje = descuentoPorcentaje; }
    public String getImagenId() { return imagenId; }
    public void setImagenId(String imagenId) { this.imagenId = imagenId; }
}
