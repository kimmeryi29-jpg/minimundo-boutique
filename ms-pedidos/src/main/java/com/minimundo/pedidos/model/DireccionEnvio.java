package com.minimundo.pedidos.model;

public class DireccionEnvio {

    private String nombreRecibe;
    private String direccion;
    private String barrio;
    private String ciudad;
    private String telefono;
    private String indicaciones;

    public String getNombreRecibe() { return nombreRecibe; }
    public void setNombreRecibe(String nombreRecibe) { this.nombreRecibe = nombreRecibe; }
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
