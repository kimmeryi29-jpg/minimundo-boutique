package com.minimundo.web.api;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * En el plan gratuito de Render cada servicio se duerme tras 15 minutos sin tráfico, y solo lo despierta tráfico que
 * llega de afuera de Render (un navegador, GitHub Actions): las llamadas entre servicios de Render no lo despiertan.
 * Por eso las páginas incluyen las URL públicas de los servicios y es el navegador del visitante el que los despierta
 * a todos a la vez (ver js/app.js). En local no hay URL configuradas y no se hace nada.
 */
@Component
public class Despertador {

    private static final List<String> VARIABLES = List.of("GATEWAY_URL", "USUARIOS_URL", "CATALOGO_URL",
            "PEDIDOS_URL", "LOGISTICA_URL", "ATENCION_URL");

    private final List<String> saludes;

    public Despertador(Environment env) {
        this.saludes = VARIABLES.stream().map(env::getProperty).filter(Objects::nonNull).map(String::trim)
                .filter(u -> u.startsWith("https://")).map(u -> u.replaceAll("/+$", "") + "/actuator/health")
                .distinct().toList();
    }

    /** URL de salud de los servicios, separadas por espacios, para el navegador. */
    public String getUrls() {
        return String.join(" ", saludes);
    }
}
