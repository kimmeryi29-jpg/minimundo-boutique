package com.minimundo.catalogo.model;

public enum Genero {
    NINA("Niña"), NINO("Niño"), UNISEX("Unisex");

    private final String texto;

    Genero(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
