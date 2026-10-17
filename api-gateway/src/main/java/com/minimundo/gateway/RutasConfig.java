package com.minimundo.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * HU-91: enruta cada petición al microservicio correspondiente según el prefijo de la ruta.
 * Las URLs de los servicios llegan por variables de entorno (en Render, sus URLs públicas HTTPS).
 */
@Configuration
public class RutasConfig {

    @Bean
    public RouteLocator rutas(RouteLocatorBuilder builder,
                              @Value("${minimundo.servicios.usuarios}") String usuarios,
                              @Value("${minimundo.servicios.catalogo}") String catalogo,
                              @Value("${minimundo.servicios.pedidos}") String pedidos,
                              @Value("${minimundo.servicios.logistica}") String logistica,
                              @Value("${minimundo.servicios.atencion}") String atencion) {
        return builder.routes()
                .route("ms-usuarios", r -> r.path("/api/auth/**", "/api/usuarios/**", "/api/auditoria/**")
                        .uri(url(usuarios)))
                .route("ms-catalogo", r -> r.path("/api/productos/**", "/api/categorias/**", "/api/imagenes/**")
                        .uri(url(catalogo)))
                .route("ms-pedidos", r -> r.path("/api/carrito/**", "/api/pedidos/**", "/api/reportes/**",
                        "/api/tarifas-envio/**").uri(url(pedidos)))
                .route("ms-logistica", r -> r.path("/api/inventario/**", "/api/envios/**", "/api/entregas/**")
                        .uri(url(logistica)))
                .route("ms-atencion", r -> r.path("/api/pqrs/**", "/api/devoluciones/**", "/api/atencion/**")
                        .uri(url(atencion)))
                .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter(@Value("${minimundo.cors.origenes:http://localhost:8080}") String origenes) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> lista = Arrays.stream(origenes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        config.setAllowedOrigins(lista);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsWebFilter(source);
    }

    static String url(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la URL de un microservicio en las variables de entorno del gateway");
        }
        String u = valor.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
