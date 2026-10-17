package com.minimundo.logistica.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Parámetros de logística configurables por el administrador (HU-82). */
@Document("parametros")
public class Parametros {

    public static final String ID = "logistica";

    @Id
    private String id = ID;
    private int stockMinimoDefecto = 5;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getStockMinimoDefecto() { return stockMinimoDefecto; }
    public void setStockMinimoDefecto(int stockMinimoDefecto) { this.stockMinimoDefecto = stockMinimoDefecto; }
}
