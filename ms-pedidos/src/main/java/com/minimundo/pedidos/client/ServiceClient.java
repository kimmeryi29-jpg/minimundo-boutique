package com.minimundo.pedidos.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimundo.pedidos.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cliente REST hacia otro microservicio. Usa el token interno de servicio y convierte cualquier falla de red
 * en un error controlado 503 en lugar de dejar la petición colgada (HU-103).
 */
public class ServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceClient.class);

    private final String nombre;
    private final RestClient rest;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public ServiceClient(String nombre, String baseUrl, ServiceTokenProvider tokens, ObjectMapper mapper,
                         Duration connectTimeout, Duration readTimeout) {
        this.nombre = nombre;
        this.tokens = tokens;
        this.mapper = mapper;
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        this.rest = RestClient.builder()
                .baseUrl(quitarBarraFinal(baseUrl))
                .requestFactory(factory)
                .build();
    }

    public <T> T get(String uri, Class<T> tipo, Object... vars) {
        return ejecutar(() -> rest.get().uri(uri, vars).header("Authorization", "Bearer " + tokens.token())
                .accept(MediaType.APPLICATION_JSON).retrieve().body(tipo));
    }

    public <T> T get(String uri, ParameterizedTypeReference<T> tipo, Object... vars) {
        return ejecutar(() -> rest.get().uri(uri, vars).header("Authorization", "Bearer " + tokens.token())
                .accept(MediaType.APPLICATION_JSON).retrieve().body(tipo));
    }

    public <T> T post(String uri, Object body, Class<T> tipo, Object... vars) {
        return ejecutar(() -> rest.post().uri(uri, vars).header("Authorization", "Bearer " + tokens.token())
                .contentType(MediaType.APPLICATION_JSON).body(body == null ? new Object() : body)
                .retrieve().body(tipo));
    }

    public <T> T post(String uri, Object body, ParameterizedTypeReference<T> tipo, Object... vars) {
        return ejecutar(() -> rest.post().uri(uri, vars).header("Authorization", "Bearer " + tokens.token())
                .contentType(MediaType.APPLICATION_JSON).body(body == null ? new Object() : body)
                .retrieve().body(tipo));
    }

    public <T> T patch(String uri, Object body, Class<T> tipo, Object... vars) {
        return ejecutar(() -> rest.patch().uri(uri, vars).header("Authorization", "Bearer " + tokens.token())
                .contentType(MediaType.APPLICATION_JSON).body(body == null ? new Object() : body)
                .retrieve().body(tipo));
    }

    private <T> T ejecutar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.BAD_GATEWAY;
            }
            if (status.is5xxServerError()) {
                log.warn("{} respondió {}: {}", nombre, status.value(), e.getResponseBodyAsString());
                throw ApiException.unavailable("El servicio de " + nombre
                        + " presentó una falla. Intenta de nuevo en unos segundos.");
            }
            throw new ApiException(status, mensajeDe(e));
        } catch (RestClientException e) {
            log.warn("No fue posible comunicarse con {}: {}", nombre, e.getMessage());
            throw ApiException.unavailable("El servicio de " + nombre
                    + " no está disponible en este momento. Intenta de nuevo en unos segundos.");
        }
    }

    private String mensajeDe(RestClientResponseException e) {
        try {
            JsonNode n = mapper.readTree(e.getResponseBodyAsString());
            if (n.hasNonNull("mensaje")) {
                return n.get("mensaje").asText();
            }
        } catch (Exception ignorada) {
            // cuerpo no JSON
        }
        return "El servicio de " + nombre + " rechazó la operación (" + e.getStatusCode().value() + ")";
    }

    private static String quitarBarraFinal(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Falta la URL de un servicio dependiente en las variables de entorno");
        }
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
