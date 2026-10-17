package com.minimundo.usuarios.controller;

import com.minimundo.usuarios.dto.AuthDtos.LoginRequest;
import com.minimundo.usuarios.dto.AuthDtos.LoginResponse;
import com.minimundo.usuarios.dto.AuthDtos.MensajeResponse;
import com.minimundo.usuarios.dto.AuthDtos.RecuperarRequest;
import com.minimundo.usuarios.dto.AuthDtos.RecuperarResponse;
import com.minimundo.usuarios.dto.AuthDtos.RegistroRequest;
import com.minimundo.usuarios.dto.AuthDtos.RestablecerRequest;
import com.minimundo.usuarios.dto.UsuarioDto;
import com.minimundo.usuarios.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Registro, inicio de sesión y recuperación de contraseña (públicos)")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-1 · Registrar un cliente")
    @ApiResponse(responseCode = "201", description = "Cuenta creada con rol CLIENTE")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "409", description = "El correo ya está en uso (HU-7)")
    public UsuarioDto registro(@Valid @RequestBody RegistroRequest req) {
        return auth.registrar(req);
    }

    @PostMapping("/login")
    @Operation(summary = "HU-2 · Iniciar sesión y obtener el token JWT")
    @ApiResponse(responseCode = "200", description = "Token JWT con el rol del usuario")
    @ApiResponse(responseCode = "401", description = "Credenciales incorrectas, cuenta bloqueada o inactiva")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/recuperar")
    @Operation(summary = "HU-6 · Solicitar el enlace de recuperación de contraseña")
    public RecuperarResponse recuperar(@Valid @RequestBody RecuperarRequest req) {
        return auth.solicitarRecuperacion(req.email());
    }

    @PostMapping("/restablecer")
    @Operation(summary = "HU-6 · Restablecer la contraseña con el enlace recibido")
    @ApiResponse(responseCode = "400", description = "Enlace vencido o ya utilizado")
    public MensajeResponse restablecer(@Valid @RequestBody RestablecerRequest req) {
        auth.restablecer(req.token(), req.password());
        return new MensajeResponse("Tu contraseña fue actualizada. Ya puedes iniciar sesión.");
    }
}
