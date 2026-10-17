package com.minimundo.catalogo.model;

/** Rangos de edad de 0 a 12 años usados en el catálogo y en los filtros (HU-16, HU-27). */
public enum RangoEdad {
    M0_12("0 a 12 meses"), A1_3("1 a 3 años"), A4_6("4 a 6 años"), A7_9("7 a 9 años"), A10_12("10 a 12 años");

    private final String texto;

    RangoEdad(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
