package com.minimundo.catalogo.service;

import java.text.Normalizer;
import java.util.Locale;

/** Utilidades de texto: normalización sin tildes para nombres y códigos. */
public final class Textos {

    private Textos() {
    }

    public static String sinTildes(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    public static String normalizar(String texto) {
        return sinTildes(texto).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Parte de un SKU: mayúsculas, sin tildes ni espacios (p. ej. "Rosa claro" → "ROSACLARO"). */
    public static String paraSku(String texto) {
        return sinTildes(texto).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static String capitalizar(String texto) {
        String t = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
    }
}
