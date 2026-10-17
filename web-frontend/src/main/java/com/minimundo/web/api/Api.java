package com.minimundo.web.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente del api-gateway: el frontend solo conoce la URL del gateway como único punto de entrada (HU-91).
 * Agrega el JWT guardado en la sesión del servidor y traduce los errores a {@link ApiException}.
 */
@Component
public class Api {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> LISTA = new TypeReference<>() { };

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final Despertador despertador;

    public Api(@Value("${minimundo.gateway-url}") String gatewayUrl, ObjectMapper mapper, Despertador despertador) {
        this.mapper = mapper;
        this.despertador = despertador;
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(90));
        String base = gatewayUrl.trim();
        if (!base.startsWith("http")) {
            base = "https://" + base;
        }
        this.rest = RestClient.builder().baseUrl(base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                .requestFactory(factory).build();
    }

    // ------------------------------------------------------------------ JSON

    public Map<String, Object> get(String uri, Object... vars) {
        return convertir(llamar(HttpMethod.GET, uri, null, vars), MAPA);
    }

    public List<Map<String, Object>> lista(String uri, Object... vars) {
        return convertir(llamar(HttpMethod.GET, uri, null, vars), LISTA);
    }

    public Map<String, Object> post(String uri, Object body, Object... vars) {
        return convertir(llamar(HttpMethod.POST, uri, body, vars), MAPA);
    }

    public Map<String, Object> put(String uri, Object body, Object... vars) {
        return convertir(llamar(HttpMethod.PUT, uri, body, vars), MAPA);
    }

    public Map<String, Object> patch(String uri, Object body, Object... vars) {
        return convertir(llamar(HttpMethod.PATCH, uri, body, vars), MAPA);
    }

    public void delete(String uri, Object... vars) {
        llamar(HttpMethod.DELETE, uri, null, vars);
    }

    /** Descarga binaria (imágenes y CSV) conservando el tipo de contenido. */
    public ResponseEntity<byte[]> descargar(String uri, Object... vars) {
        try {
            return rest.get().uri(uri, vars).headers(this::autorizar).retrieve().toEntity(byte[].class);
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (RestClientException e) {
            throw noDisponible();
        }
    }

    /** HU-18: envía las imágenes al catálogo como multipart. */
    public Map<String, Object> subirImagenes(String uri, List<MultipartFile> archivos, Object... vars) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        for (MultipartFile f : archivos) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            try {
                byte[] datos = f.getBytes();
                String nombre = f.getOriginalFilename();
                HttpHeaders h = new HttpHeaders();
                h.setContentType(f.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(f.getContentType()));
                h.setContentDispositionFormData("archivos", nombre);
                partes.add("archivos", new org.springframework.http.HttpEntity<>(new ByteArrayResource(datos) {
                    @Override
                    public String getFilename() {
                        return nombre;
                    }
                }, h));
            } catch (IOException e) {
                throw new ApiException(400, "No fue posible leer la imagen", null);
            }
        }
        if (partes.isEmpty()) {
            throw new ApiException(400, "Selecciona al menos una imagen", null);
        }
        try {
            String json = rest.post().uri(uri, vars).headers(this::autorizar).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes).retrieve().body(String.class);
            return convertir(json, MAPA);
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (RestClientException e) {
            throw noDisponible();
        }
    }

    /** Login sin sesión previa (lo usa el proveedor de autenticación). */
    public Map<String, Object> login(String email, String password) {
        try {
            String json = rest.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", password)).retrieve().body(String.class);
            return convertir(json, MAPA);
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (RestClientException e) {
            throw noDisponible();
        }
    }

    // ------------------------------------------------------------------ Internos

    private String llamar(HttpMethod metodo, String uri, Object body, Object... vars) {
        try {
            RestClient.RequestBodySpec spec = rest.method(metodo).uri(uri, vars).headers(this::autorizar)
                    .accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return spec.retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (RestClientException e) {
            throw noDisponible();
        }
    }

    private void autorizar(HttpHeaders h) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionUser u) {
            h.setBearerAuth(u.token());
        }
    }

    private <T> T convertir(String json, TypeReference<T> tipo) {
        try {
            if (json == null || json.isBlank()) {
                return tipo == LISTA ? mapper.readValue("[]", tipo) : mapper.readValue("{}", tipo);
            }
            return mapper.readValue(json, tipo);
        } catch (IOException e) {
            throw new ApiException(502, "La respuesta del servidor no tiene el formato esperado", null);
        }
    }

    private ApiException error(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String mensaje = null;
        Map<String, String> campos = new HashMap<>();
        try {
            JsonNode n = mapper.readTree(e.getResponseBodyAsString());
            if (n.hasNonNull("mensaje")) {
                mensaje = n.get("mensaje").asText();
            }
            if (n.has("campos") && n.get("campos").isObject()) {
                n.get("campos").fields().forEachRemaining(c -> campos.put(c.getKey(), c.getValue().asText()));
            }
        } catch (Exception ignorada) {
            // cuerpo vacío o no JSON
        }
        if (status >= 500) {
            despertador.despertar();
        }
        if (mensaje == null) {
            mensaje = switch (status) {
                case 401 -> "Tu sesión expiró. Inicia sesión de nuevo.";
                case 403 -> "No tienes permiso para realizar esta operación.";
                case 404 -> "No encontramos lo que buscabas.";
                default -> status >= 500 ? "Un servicio no respondió. Intenta de nuevo en unos segundos."
                        : "No fue posible completar la operación.";
            };
        }
        return new ApiException(status, mensaje, campos);
    }

    private ApiException noDisponible() {
        despertador.despertar();
        return new ApiException(503, "La plataforma se está iniciando o un servicio no responde. "
                + "Espera unos segundos y vuelve a intentarlo.", null);
    }
}
