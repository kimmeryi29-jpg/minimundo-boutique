package com.minimundo.pedidos.service;

import com.minimundo.pedidos.client.ServiceClient;
import com.minimundo.pedidos.dto.PedidoDtos.PrecioSku;
import com.minimundo.pedidos.dto.PedidoDtos.PreciosRequest;
import com.minimundo.pedidos.dto.PedidoDtos.ProductoActivo;
import com.minimundo.pedidos.dto.PedidoDtos.ReservaRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Llamadas de ms-pedidos a ms-catalogo (precios vigentes) y a ms-logistica (stock y reservas). */
@Component
public class Integraciones {

    private final ServiceClient catalogo;
    private final ServiceClient logistica;

    public Integraciones(@Qualifier("catalogoClient") ServiceClient catalogo,
                         @Qualifier("logisticaClient") ServiceClient logistica) {
        this.catalogo = catalogo;
        this.logistica = logistica;
    }

    public Map<String, PrecioSku> precios(List<String> skus) {
        Map<String, PrecioSku> res = new HashMap<>();
        if (skus.isEmpty()) {
            return res;
        }
        List<PrecioSku> lista = catalogo.post("/api/productos/interno/precios", new PreciosRequest(skus),
                new ParameterizedTypeReference<List<PrecioSku>>() { });
        if (lista != null) {
            lista.forEach(p -> res.put(p.sku(), p));
        }
        return res;
    }

    public List<ProductoActivo> productosActivos() {
        return catalogo.get("/api/productos/interno/activos", new ParameterizedTypeReference<List<ProductoActivo>>() { });
    }

    public Map<String, Integer> disponibilidad(List<String> skus) {
        if (skus.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> r = logistica.get("/api/inventario/disponibilidad?skus={skus}",
                new ParameterizedTypeReference<Map<String, Integer>>() { }, String.join(",", skus));
        return r == null ? Map.of() : r;
    }

    public void reservar(ReservaRequest req) {
        logistica.post("/api/inventario/reservas", req, Object.class);
    }

    public void liberar(String pedidoId) {
        logistica.post("/api/inventario/reservas/{id}/liberar", null, Object.class, pedidoId);
    }
}
