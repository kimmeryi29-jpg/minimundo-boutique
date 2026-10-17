package com.minimundo.usuarios.service;

import com.minimundo.usuarios.dto.AuthDtos.LoginRequest;
import com.minimundo.usuarios.dto.AuthDtos.LoginResponse;
import com.minimundo.usuarios.dto.AuthDtos.RecuperarResponse;
import com.minimundo.usuarios.dto.AuthDtos.RegistroRequest;
import com.minimundo.usuarios.dto.UsuarioDto;
import com.minimundo.usuarios.error.ApiException;
import com.minimundo.usuarios.model.Rol;
import com.minimundo.usuarios.model.TipoEvento;
import com.minimundo.usuarios.model.Usuario;
import com.minimundo.usuarios.repository.UsuarioRepository;
import com.minimundo.usuarios.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Registro (HU-1, HU-7), inicio de sesión con JWT (HU-2, HU-3), bloqueo por intentos fallidos (HU-13),
 * cuentas inactivas (HU-11) y recuperación de contraseña (HU-6).
 */
@Service
public class AuthService {

    public static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditoriaService auditoria;
    private final MailService mail;
    private final Clock clock;
    private final int maxIntentos;
    private final Duration duracionBloqueo;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt,
                       AuditoriaService auditoria, MailService mail, Clock clock,
                       @Value("${minimundo.seguridad.max-intentos:5}") int maxIntentos,
                       @Value("${minimundo.seguridad.minutos-bloqueo:15}") long minutosBloqueo,
                       @Value("${minimundo.frontend-url:http://localhost:8080}") String frontendUrl) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.auditoria = auditoria;
        this.mail = mail;
        this.clock = clock;
        this.maxIntentos = maxIntentos;
        this.duracionBloqueo = Duration.ofMinutes(minutosBloqueo);
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** HU-1: crea la cuenta con rol CLIENTE. HU-7: rechaza correos ya registrados (sin importar mayúsculas). */
    public UsuarioDto registrar(RegistroRequest req) {
        String email = normalizarEmail(req.email());
        if (usuarios.existsByEmail(email)) {
            throw ApiException.conflict("El correo ya está en uso");
        }
        Usuario u = new Usuario();
        u.setNombre(req.nombre().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRol(Rol.CLIENTE);
        u.setActivo(true);
        u.setFechaCreacion(Instant.now(clock));
        return UsuarioDto.de(usuarios.save(u));
    }

    /** HU-2, HU-3, HU-11, HU-13. */
    public LoginResponse login(LoginRequest req) {
        String email = normalizarEmail(req.email());
        Usuario u = usuarios.findByEmail(email).orElse(null);
        if (u == null) {
            auditoria.registrar(TipoEvento.LOGIN_FALLIDO, email, null, "FALLIDO", "Correo no registrado", email);
            throw ApiException.unauthorized(CREDENCIALES_INVALIDAS);
        }
        Instant ahora = Instant.now(clock);
        if (u.getBloqueadoHasta() != null && u.getBloqueadoHasta().isAfter(ahora)) {
            long minutos = Math.max(1, Duration.between(ahora, u.getBloqueadoHasta()).toSeconds() / 60 + 1);
            auditoria.registrar(TipoEvento.LOGIN_FALLIDO, email, u.getId(), "BLOQUEADO",
                    "Intento con la cuenta bloqueada", email);
            throw ApiException.unauthorized("Tu cuenta está bloqueada temporalmente por varios intentos fallidos. "
                    + "Intenta de nuevo en " + minutos + (minutos == 1 ? " minuto." : " minutos."));
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            int intentos = u.getIntentosFallidos() + 1;
            u.setIntentosFallidos(intentos);
            if (intentos >= maxIntentos) {
                u.setBloqueadoHasta(ahora.plus(duracionBloqueo));
                u.setIntentosFallidos(0);
                usuarios.save(u);
                auditoria.registrar(TipoEvento.CUENTA_BLOQUEADA, email, u.getId(), "BLOQUEADO",
                        "Cuenta bloqueada por " + duracionBloqueo.toMinutes() + " minutos tras " + maxIntentos
                                + " intentos fallidos", email);
                throw ApiException.unauthorized("Superaste el número de intentos permitidos. Tu cuenta quedó "
                        + "bloqueada por " + duracionBloqueo.toMinutes() + " minutos.");
            }
            usuarios.save(u);
            auditoria.registrar(TipoEvento.LOGIN_FALLIDO, email, u.getId(), "FALLIDO", "Contraseña incorrecta", email);
            throw ApiException.unauthorized(CREDENCIALES_INVALIDAS);
        }
        if (!u.isActivo()) {
            auditoria.registrar(TipoEvento.LOGIN_FALLIDO, email, u.getId(), "DENEGADO", "Usuario inactivo", email);
            throw ApiException.unauthorized("Tu cuenta está desactivada. Comunícate con la boutique.");
        }
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        u.setUltimoAcceso(ahora);
        usuarios.save(u);
        auditoria.registrar(TipoEvento.LOGIN_EXITOSO, email, u.getId(), "EXITOSO", "Rol " + u.getRol(), email);
        String token = jwt.generar(u.getId(), u.getEmail(), u.getNombre(), u.getRol().name());
        return new LoginResponse(token, "Bearer", jwt.getExpiracion().toSeconds(), UsuarioDto.de(u));
    }

    /** HU-6: genera un enlace de un solo uso válido por 30 minutos. La respuesta no revela si el correo existe. */
    public RecuperarResponse solicitarRecuperacion(String emailIngresado) {
        String email = normalizarEmail(emailIngresado);
        String mensaje = "Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña.";
        Usuario u = usuarios.findByEmail(email).orElse(null);
        if (u == null || !u.isActivo()) {
            return new RecuperarResponse(mensaje, null);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        u.setResetTokenHash(sha256(token));
        u.setResetTokenExpira(Instant.now(clock).plus(Duration.ofMinutes(30)));
        usuarios.save(u);
        String enlace = frontendUrl + "/restablecer?token=" + token;
        boolean enviado = mail.enviarRecuperacion(u.getEmail(), u.getNombre(), enlace);
        auditoria.registrar(TipoEvento.RECUPERACION_SOLICITADA, email, u.getId(), enviado ? "ENVIADO" : "DEMO",
                "Enlace de recuperación generado", email);
        if (!enviado) {
            return new RecuperarResponse("Modo demostración: no hay servidor de correo configurado, "
                    + "así que te mostramos el enlace aquí.", enlace);
        }
        return new RecuperarResponse(mensaje, null);
    }

    /** HU-6: el enlace vencido o ya utilizado se rechaza. */
    public void restablecer(String token, String nuevaPassword) {
        Usuario u = usuarios.findByResetTokenHash(sha256(token)).orElse(null);
        if (u == null || u.getResetTokenExpira() == null || u.getResetTokenExpira().isBefore(Instant.now(clock))) {
            throw ApiException.badRequest("El enlace no es válido, ya fue utilizado o venció. Solicita uno nuevo.");
        }
        u.setPasswordHash(encoder.encode(nuevaPassword));
        u.setResetTokenHash(null);
        u.setResetTokenExpira(null);
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        usuarios.save(u);
        auditoria.registrar(TipoEvento.CONTRASENA_RESTABLECIDA, u.getEmail(), u.getId(), "EXITOSO",
                "Contraseña restablecida con enlace", u.getEmail());
    }

    static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
