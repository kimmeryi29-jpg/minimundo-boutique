package com.minimundo.usuarios.model;

/** Dirección de envío guardada en el perfil del cliente (HU-5). */
public class Direccion {

    private String direccion;
    private String barrio;
    private String ciudad;
    private String telefono;
    private String indicaciones;

    public Direccion() {
    }

    public Direccion(String direccion, String barrio, String ciudad, String telefono, String indicaciones) {
        this.direccion = direccion;
        this.barrio = barrio;
        this.ciudad = ciudad;
        this.telefono = telefono;
        this.indicaciones = indicaciones;
    }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getBarrio() { return barrio; }
    public void setBarrio(String barrio) { this.barrio = barrio; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getIndicaciones() { return indicaciones; }
    public void setIndicaciones(String indicaciones) { this.indicaciones = indicaciones; }
}
