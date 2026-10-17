package com.minimundo.web.api;

import java.io.Serializable;

/** Usuario en la sesión HTTP del servidor. El JWT nunca llega al navegador (sección 5.6). */
public record SessionUser(String id, String nombre, String email, String rol, String token) implements Serializable {

    public String inicio() {
        return switch (rol) {
            case "ADMIN" -> "/admin";
            case "ASESOR" -> "/asesor/pqrs";
            case "OPERARIO" -> "/operario/alistamiento";
            case "REPARTIDOR" -> "/repartidor/entregas";
            default -> "/";
        };
    }

    public String primerNombre() {
        String n = nombre == null ? "" : nombre.replaceAll("\\(.*\\)", "").trim();
        int i = n.indexOf(' ');
        return i > 0 ? n.substring(0, i) : n;
    }
}
