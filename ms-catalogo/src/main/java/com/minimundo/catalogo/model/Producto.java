package com.minimundo.catalogo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento Producto (db_catalogo.productos). Las variantes se modelan como arreglo embebido (sección 5.4).
 * El índice único multiclave sobre variantes.sku garantiza que un SKU no se repita en todo el catálogo (HU-25).
 */
@Document("productos")
@CompoundIndex(name = "sku_unico", def = "{'variantes.sku': 1}", unique = true, sparse = true)
public class Producto {

    @Id
    private String id;
    @Indexed(unique = true)
    private String codigo;
    private String nombre;
    private String descripcion;
    @Indexed
    private String categoriaId;
    private List<String> coleccionIds = new ArrayList<>();
    private Genero genero;
    private RangoEdad rangoEdad;
    private long precio;
    private List<ImagenRef> imagenes = new ArrayList<>();
    private List<Variante> variantes = new ArrayList<>();
    private EstadoProducto estado = EstadoProducto.BORRADOR;
    private boolean activo = true;
    private Descuento descuento;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Instant fechaPublicacion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCategoriaId() { return categoriaId; }
    public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }
    public List<String> getColeccionIds() { return coleccionIds; }
    public void setColeccionIds(List<String> coleccionIds) { this.coleccionIds = coleccionIds == null ? new ArrayList<>() : coleccionIds; }
    public Genero getGenero() { return genero; }
    public void setGenero(Genero genero) { this.genero = genero; }
    public RangoEdad getRangoEdad() { return rangoEdad; }
    public void setRangoEdad(RangoEdad rangoEdad) { this.rangoEdad = rangoEdad; }
    public long getPrecio() { return precio; }
    public void setPrecio(long precio) { this.precio = precio; }
    public List<ImagenRef> getImagenes() { return imagenes; }
    public void setImagenes(List<ImagenRef> imagenes) { this.imagenes = imagenes == null ? new ArrayList<>() : imagenes; }
    public List<Variante> getVariantes() { return variantes; }
    public void setVariantes(List<Variante> variantes) { this.variantes = variantes == null ? new ArrayList<>() : variantes; }
    public EstadoProducto getEstado() { return estado; }
    public void setEstado(EstadoProducto estado) { this.estado = estado; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public Descuento getDescuento() { return descuento; }
    public void setDescuento(Descuento descuento) { this.descuento = descuento; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(Instant fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
    public Instant getFechaPublicacion() { return fechaPublicacion; }
    public void setFechaPublicacion(Instant fechaPublicacion) { this.fechaPublicacion = fechaPublicacion; }

    public boolean visibleParaClientes() {
        return activo && estado == EstadoProducto.PUBLICADO;
    }
}
