package com.minimundo.catalogo.model;

/** Referencia a una imagen guardada en GridFS dentro de db_catalogo (HU-18). */
public class ImagenRef {

    private String id;
    private String nombre;
    private String contentType;

    public ImagenRef() {
    }

    public ImagenRef(String id, String nombre, String contentType) {
        this.id = id;
        this.nombre = nombre;
        this.contentType = contentType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
