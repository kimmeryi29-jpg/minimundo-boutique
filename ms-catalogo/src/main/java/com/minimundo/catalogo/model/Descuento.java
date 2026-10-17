package com.minimundo.catalogo.model;

import java.time.LocalDate;

/** HU-23: descuento porcentual con fecha de inicio y fin (ambas inclusive). */
public class Descuento {

    private int porcentaje;
    private LocalDate inicio;
    private LocalDate fin;

    public Descuento() {
    }

    public Descuento(int porcentaje, LocalDate inicio, LocalDate fin) {
        this.porcentaje = porcentaje;
        this.inicio = inicio;
        this.fin = fin;
    }

    public boolean vigente(LocalDate hoy) {
        return porcentaje > 0 && inicio != null && fin != null && !hoy.isBefore(inicio) && !hoy.isAfter(fin);
    }

    public int getPorcentaje() { return porcentaje; }
    public void setPorcentaje(int porcentaje) { this.porcentaje = porcentaje; }
    public LocalDate getInicio() { return inicio; }
    public void setInicio(LocalDate inicio) { this.inicio = inicio; }
    public LocalDate getFin() { return fin; }
    public void setFin(LocalDate fin) { this.fin = fin; }
}
