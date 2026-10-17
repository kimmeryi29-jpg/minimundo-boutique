package com.minimundo.catalogo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Categoría o colección (por edad o temporada) de db_catalogo.categorias (HU-21). */
@Document("categorias")
public class Categoria {

    @Id
    private String id;
    private String nombre;
    /** Nombre en minúsculas y sin tildes: dos categorías no pueden llamarse igual (HU-21). */
    @Indexed(unique = true)
    private String nombreNormalizado;
    private TipoCategoria tipo;
    private String descripcion;
    private Descuento descuento;
    private Instant fechaCreacion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getNombreNormalizado() { return nombreNormalizado; }
    public void setNombreNormalizado(String nombreNormalizado) { this.nombreNormalizado = nombreNormalizado; }
    public TipoCategoria getTipo() { return tipo; }
    public void setTipo(TipoCategoria tipo) { this.tipo = tipo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Descuento getDescuento() { return descuento; }
    public void setDescuento(Descuento descuento) { this.descuento = descuento; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
