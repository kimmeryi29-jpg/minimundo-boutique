package com.minimundo.web.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * En el plan gratuito de Render cada servicio se duerme tras 15 minutos sin tráfico. Cuando la tienda arranca, o
 * cuando el gateway no responde, se despiertan todos los servicios a la vez (en paralelo) en lugar de uno tras otro,
 * para que la plataforma completa esté lista en cerca de un minuto. Como mucho una vez cada dos minutos.
 */
@Component
public class Despertador {

    private static final Logger log = LoggerFactory.getLogger(Despertador.class);
    private static final List<String> VARIABLES = List.of("GATEWAY_URL", "USUARIOS_URL", "CATALOGO_URL",
            "PEDIDOS_URL", "LOGISTICA_URL", "ATENCION_URL");

    private final List<String> urls;
    private final AtomicLong ultimo = new AtomicLong();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Despertador(Environment env) {
        this.urls = VARIABLES.stream().map(env::getProperty).filter(Objects::nonNull).map(String::trim)
                .filter(u -> u.startsWith("http")).distinct().toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        despertar();
    }

    public void despertar() {
        long ahora = System.currentTimeMillis();
        long antes = ultimo.get();
        if (urls.isEmpty() || ahora - antes < 120_000 || !ultimo.compareAndSet(antes, ahora)) {
            return;
        }
        for (String url : urls) {
            String salud = (url.endsWith("/") ? url.substring(0, url.length() - 1) : url) + "/actuator/health";
            http.sendAsync(HttpRequest.newBuilder(URI.create(salud)).timeout(Duration.ofSeconds(150)).GET().build(),
                            HttpResponse.BodyHandlers.discarding())
                    .whenComplete((r, e) -> log.info("Despertar {} -> {}", salud, e == null ? r.statusCode() : e.getClass().getSimpleName()));
        }
    }
}
