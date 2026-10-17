package com.minimundo.gateway;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * HU-92: valida la firma y la expiración del JWT antes de enrutar. Un token inválido o vencido recibe 401
 * y la petición no llega al microservicio. Las rutas internas entre servicios responden 404 desde afuera.
 */
@Component
public class JwtFilter implements GlobalFilter, Ordered {

    private final SecretKey key;

    public JwtFilter(@Value("${minimundo.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno JWT_SECRET en el api-gateway.");
        }
        try {
            this.key = Keys.hmacShaKeyFor(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (RutasPublicas.esInterna(path)) {
            return error(exchange.getResponse(), HttpStatus.NOT_FOUND, "Recurso no encontrado", path);
        }
        if (RutasPublicas.esPublica(exchange.getRequest().getMethod(), path)) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return error(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Debes iniciar sesión", path);
        }
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(header.substring(7).trim());
        } catch (JwtException | IllegalArgumentException e) {
            return error(exchange.getResponse(), HttpStatus.UNAUTHORIZED,
                    "Tu sesión expiró o el token no es válido. Inicia sesión de nuevo.", path);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> error(ServerHttpResponse res, HttpStatus status, String mensaje, String path) {
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"codigo\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"mensaje\":\"" + mensaje + "\",\"fecha\":\"" + OffsetDateTime.now(ZoneId.of("America/Bogota"))
                + "\",\"ruta\":\"" + path.replace("\"", "") + "\"}";
        DataBuffer buffer = res.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return res.writeWith(Mono.just(buffer));
    }
}
