package com.minimundo.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas de autenticación (HU-1, HU-2, HU-6). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegistroRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "Máximo 100 caracteres") String nombre,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido") String email,
            @NotBlank(message = "La contraseña es obligatoria") @Size(min = 8, max = 72, message = "La contraseña debe tener mínimo 8 caracteres") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    public record LoginResponse(String token, String tipo, long expiraEnSegundos, UsuarioDto usuario) {
    }

    public record RecuperarRequest(
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no tiene un formato válido") String email) {
    }

    /** enlaceDemo solo se llena cuando no hay servidor de correo configurado (modo demostración). */
    public record RecuperarResponse(String mensaje, String enlaceDemo) {
    }

    public record RestablecerRequest(
            @NotBlank(message = "El enlace no es válido") String token,
            @NotBlank(message = "La contraseña es obligatoria") @Size(min = 8, max = 72, message = "La contraseña debe tener mínimo 8 caracteres") String password) {
    }

    public record MensajeResponse(String mensaje) {
    }
}
