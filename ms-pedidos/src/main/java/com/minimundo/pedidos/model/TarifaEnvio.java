package com.minimundo.pedidos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Costo de envío por ciudad, configurable sin cambiar el código (HU-38, HU-81). */
@Document("tarifas_envio")
public class TarifaEnvio {

    @Id
    private String id;
    private String ciudad;
    @Indexed(unique = true)
    private String ciudadNormalizada;
    private long costo;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public String getCiudadNormalizada() { return ciudadNormalizada; }
    public void setCiudadNormalizada(String ciudadNormalizada) { this.ciudadNormalizada = ciudadNormalizada; }
    public long getCosto() { return costo; }
    public void setCosto(long costo) { this.costo = costo; }
}
