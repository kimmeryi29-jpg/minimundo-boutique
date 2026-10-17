package com.minimundo.logistica.security;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Usuario autenticado extraído del JWT (identificador, correo, nombre y rol).
 */
public record AuthUser(String id, String email, String nombre, String rol) implements Serializable {

    public boolean tieneRol(String... roles) {
        return Arrays.asList(roles).contains(rol);
    }

    public boolean esServicio() {
        return "SERVICIO".equals(rol);
    }
}
