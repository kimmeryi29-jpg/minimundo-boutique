package com.minimundo.atencion.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** HU-83 y HU-84: plazo de cambios y devoluciones y días hábiles de vencimiento de una PQRS (valor inicial 5). */
@Document("parametros")
public class Parametros {

    public static final String ID = "atencion";

    @Id
    private String id = ID;
    private int plazoCambiosDias = 30;
    private int diasVencimientoPqrs = 5;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getPlazoCambiosDias() { return plazoCambiosDias; }
    public void setPlazoCambiosDias(int plazoCambiosDias) { this.plazoCambiosDias = plazoCambiosDias; }
    public int getDiasVencimientoPqrs() { return diasVencimientoPqrs; }
    public void setDiasVencimientoPqrs(int diasVencimientoPqrs) { this.diasVencimientoPqrs = diasVencimientoPqrs; }
}
