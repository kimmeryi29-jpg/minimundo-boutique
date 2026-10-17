package com.minimundo.usuarios.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Firma y valida los JWT. El secreto se lee de la variable de entorno JWT_SECRET (HU-95).
 * La clave HMAC se deriva con SHA-256 del secreto, igual en todos los servicios.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiracion;
    private final String emisor;

    public JwtService(@Value("${minimundo.jwt.secret:}") String secret,
                      @Value("${minimundo.jwt.expiracion-minutos:480}") long minutos,
                      @Value("${spring.application.name}") String emisor) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno JWT_SECRET. Configúrala antes de iniciar el servicio.");
        }
        this.key = Keys.hmacShaKeyFor(sha256(secret));
        this.expiracion = Duration.ofMinutes(minutos);
        this.emisor = emisor;
    }

    public String generar(String id, String email, String nombre, String rol) {
        return generar(id, email, nombre, rol, expiracion);
    }

    public String generar(String id, String email, String nombre, String rol, Duration duracion) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .issuer(emisor)
                .subject(id)
                .claim("email", email)
                .claim("nombre", nombre)
                .claim("rol", rol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> validar(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(c.getSubject(), c.get("email", String.class),
                    c.get("nombre", String.class), c.get("rol", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration getExpiracion() {
        return expiracion;
    }

    private static byte[] sha256(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
