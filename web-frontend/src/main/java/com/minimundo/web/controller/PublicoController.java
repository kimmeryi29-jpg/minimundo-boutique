package com.minimundo.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimundo.web.api.Api;
import com.minimundo.web.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tienda pública: portada, catálogo con filtros (HU-27, HU-28) y detalle con disponibilidad (HU-26, HU-29, HU-30). */
@Controller
public class PublicoController {

    private final Api api;
    private final ObjectMapper mapper;

    public PublicoController(Api api, ObjectMapper mapper) {
        this.api = api;
        this.mapper = mapper;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("opciones", api.get("/api/productos/opciones"));
        model.addAttribute("colecciones", api.lista("/api/categorias?tipo=TEMPORADA"));
        model.addAttribute("categorias", api.lista("/api/categorias?tipo=CATEGORIA"));
        List<Map<String, Object>> productos = api.lista("/api/productos");
        model.addAttribute("ofertas", productos.stream().filter(p -> ((Number) p.get("descuentoPorcentaje")).intValue() > 0).limit(4).toList());
        model.addAttribute("nuevos", productos.stream().limit(8).toList());
        return "publico/inicio";
    }

    @GetMapping("/catalogo")
    public String catalogo(@RequestParam(required = false) String q, @RequestParam(required = false) String categoria,
                           @RequestParam(required = false) String coleccion, @RequestParam(required = false) String edad,
                           @RequestParam(required = false) String talla, @RequestParam(required = false) String genero,
                           @RequestParam(required = false) Long precioMax, @RequestParam(required = false) String orden,
                           Model model) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/api/productos");
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("q", q);
        filtros.put("categoria", categoria);
        filtros.put("coleccion", coleccion);
        filtros.put("edad", edad);
        filtros.put("talla", talla);
        filtros.put("genero", genero);
        filtros.put("precioMax", precioMax);
        filtros.put("orden", orden);
        filtros.forEach((k, v) -> {
            if (v != null && !v.toString().isBlank()) {
                b.queryParam(k, v);
            }
        });
        model.addAttribute("productos", api.lista(b.build().toUriString()));
        model.addAttribute("filtros", filtros);
        model.addAttribute("hayFiltros", filtros.values().stream().anyMatch(v -> v != null && !v.toString().isBlank()));
        model.addAttribute("opciones", api.get("/api/productos/opciones"));
        model.addAttribute("categorias", api.lista("/api/categorias?tipo=CATEGORIA"));
        List<Map<String, Object>> colecciones = new ArrayList<>(api.lista("/api/categorias?tipo=EDAD"));
        colecciones.addAll(api.lista("/api/categorias?tipo=TEMPORADA"));
        model.addAttribute("colecciones", colecciones);
        return "publico/catalogo";
    }

    @GetMapping("/producto/{id}")
    @SuppressWarnings("unchecked")
    public String producto(@PathVariable String id, Model model) throws JsonProcessingException {
        Map<String, Object> p;
        try {
            p = api.get("/api/productos/{id}", id);
        } catch (ApiException e) {
            if (e.getStatus() == 404) {
                model.addAttribute("mensaje", "Este producto ya no está disponible.");
                return "publico/no-disponible";
            }
            throw e;
        }
        List<Map<String, Object>> variantes = (List<Map<String, Object>>) p.get("variantes");
        String skus = String.join(",", variantes.stream().map(v -> v.get("sku").toString()).toList());
        Map<String, Object> disp = skus.isEmpty() ? Map.of() : api.get("/api/inventario/disponibilidad?skus={s}", skus);
        List<Map<String, Object>> conDisponibilidad = new ArrayList<>();
        for (Map<String, Object> v : variantes) {
            Map<String, Object> x = new LinkedHashMap<>(v);
            Object d = disp.get(v.get("sku").toString());
            x.put("disponible", d == null ? 0 : ((Number) d).intValue());
            conDisponibilidad.add(x);
        }
        model.addAttribute("p", p);
        model.addAttribute("variantes", conDisponibilidad);
        model.addAttribute("variantesJson", mapper.writeValueAsString(conDisponibilidad));
        model.addAttribute("agotadoTotal", conDisponibilidad.stream().allMatch(v -> ((Integer) v.get("disponible")) <= 0));
        return "publico/producto";
    }

    /** Las imágenes se sirven desde web-frontend: el navegador solo habla con este servicio. */
    @GetMapping("/img/{id}")
    public ResponseEntity<byte[]> imagen(@PathVariable String id) {
        ResponseEntity<byte[]> r = api.descargar("/api/imagenes/{id}", id);
        return ResponseEntity.ok().contentType(r.getHeaders().getContentType())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(r.getBody());
    }

    @GetMapping("/403")
    public String prohibido() {
        return "error/403";
    }
}
