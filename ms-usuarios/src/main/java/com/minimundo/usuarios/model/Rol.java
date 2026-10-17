package com.minimundo.usuarios.model;

/** Los cinco roles del dominio de MiniMundo Boutique. */
public enum Rol {
    CLIENTE, ASESOR, OPERARIO, REPARTIDOR, ADMIN;

    /** Roles que solo puede crear el administrador (HU-8). */
    public boolean esInterno() {
        return this != CLIENTE;
    }
}
