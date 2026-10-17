package com.minimundo.usuarios.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Peticiones de gestión de usuarios y perfil. */
public final class UsuarioRequests {

    private UsuarioRequests() {
    }

    /** HU-8: el administrador crea usuarios internos. */
    public record CrearUsuarioRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "Máximo 100 caracteres") String nombre,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido") String email,
            @NotBlank(message = "La contraseña es obligatoria") @Size(min = 8, max = 72, message = "La contraseña debe tener mínimo 8 caracteres") String password,
            @NotBlank(message = "El rol es obligatorio") String rol,
            String telefono) {
    }

    /** HU-10: actualizar datos y rol. */
    public record ActualizarUsuarioRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "Máximo 100 caracteres") String nombre,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido") String email,
            @NotBlank(message = "El rol es obligatorio") String rol,
            String telefono) {
    }

    public record DireccionRequest(
            @NotBlank(message = "La dirección es obligatoria") @Size(max = 150) String direccion,
            @Size(max = 80) String barrio,
            @NotBlank(message = "La ciudad es obligatoria") @Size(max = 80) String ciudad,
            @NotBlank(message = "El teléfono es obligatorio") @Size(max = 20) String telefono,
            @Size(max = 200) String indicaciones) {
    }

    /** HU-5: el cliente actualiza sus datos personales y su dirección de envío. */
    public record PerfilRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "Máximo 100 caracteres") String nombre,
            @Size(max = 20) String telefono,
            @Valid DireccionRequest direccion) {
    }
}
