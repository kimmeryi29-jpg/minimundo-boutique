package com.minimundo.atencion.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Consecutivos para los números de radicado. */
@Document("contadores")
public class Contador {

    @Id
    private String id;
    private long valor;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getValor() { return valor; }
    public void setValor(long valor) { this.valor = valor; }
}
