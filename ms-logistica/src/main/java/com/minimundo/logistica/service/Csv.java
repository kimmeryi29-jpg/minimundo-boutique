package com.minimundo.logistica.service;

/** Escapa valores para archivos CSV. */
public final class Csv {

    private Csv() {
    }

    public static String campo(Object valor) {
        if (valor == null) {
            return "";
        }
        String s = valor.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
