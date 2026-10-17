package com.minimundo.usuarios.dto;

import com.minimundo.usuarios.model.Direccion;
import com.minimundo.usuarios.model.Usuario;

import java.time.Instant;

/** Vista pública de un usuario: nunca incluye la contraseña ni datos internos. */
public record UsuarioDto(String id, String nombre, String email, String rol, boolean activo, String telefono,
                         Direccion direccion, Instant fechaCreacion, Instant ultimoAcceso) {

    public static UsuarioDto de(Usuario u) {
        return new UsuarioDto(u.getId(), u.getNombre(), u.getEmail(), u.getRol().name(), u.isActivo(),
                u.getTelefono(), u.getDireccion(), u.getFechaCreacion(), u.getUltimoAcceso());
    }
}
