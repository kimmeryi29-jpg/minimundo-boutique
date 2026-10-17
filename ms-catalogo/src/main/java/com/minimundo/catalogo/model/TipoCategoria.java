package com.minimundo.catalogo.model;

/** HU-21: categorías de producto y colecciones por edad o por temporada. */
public enum TipoCategoria {
    CATEGORIA("Categoría"), EDAD("Colección por edad"), TEMPORADA("Colección de temporada");

    private final String texto;

    TipoCategoria(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
