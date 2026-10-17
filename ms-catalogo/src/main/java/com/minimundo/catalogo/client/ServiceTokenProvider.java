package com.minimundo.catalogo.client;

import com.minimundo.catalogo.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Emite un JWT de corta duración con rol SERVICIO para las llamadas internas entre microservicios.
 */
@Component
public class ServiceTokenProvider {

    private static final Duration DURACION = Duration.ofMinutes(10);

    private final JwtService jwt;
    private final String nombre;
    private volatile String token;
    private volatile Instant vence = Instant.EPOCH;

    public ServiceTokenProvider(JwtService jwt, @Value("${spring.application.name}") String nombre) {
        this.jwt = jwt;
        this.nombre = nombre;
    }

    public String token() {
        if (token == null || Instant.now().isAfter(vence.minusSeconds(60))) {
            synchronized (this) {
                if (token == null || Instant.now().isAfter(vence.minusSeconds(60))) {
                    token = jwt.generar(nombre, nombre + "@servicio", nombre, "SERVICIO", DURACION);
                    vence = Instant.now().plus(DURACION);
                }
            }
        }
        return token;
    }
}
