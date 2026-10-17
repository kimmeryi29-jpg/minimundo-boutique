package com.minimundo.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class RutasPublicasTest {

    @Test
    void hu92_registroLoginYCatalogoSonPublicos() {
        assertThat(RutasPublicas.esPublica(HttpMethod.POST, "/api/auth/login")).isTrue();
        assertThat(RutasPublicas.esPublica(HttpMethod.POST, "/api/auth/registro")).isTrue();
        assertThat(RutasPublicas.esPublica(HttpMethod.GET, "/api/productos")).isTrue();
        assertThat(RutasPublicas.esPublica(HttpMethod.GET, "/api/productos/abc123")).isTrue();
        assertThat(RutasPublicas.esPublica(HttpMethod.GET, "/api/categorias")).isTrue();
    }

    @Test
    void hu92_rutasProtegidasExigenToken() {
        assertThat(RutasPublicas.esPublica(HttpMethod.GET, "/api/productos/gestion")).isFalse();
        assertThat(RutasPublicas.esPublica(HttpMethod.POST, "/api/productos")).isFalse();
        assertThat(RutasPublicas.esPublica(HttpMethod.GET, "/api/usuarios")).isFalse();
        assertThat(RutasPublicas.esPublica(HttpMethod.GET, "/api/pedidos/mis-pedidos")).isFalse();
        assertThat(RutasPublicas.esPublica(HttpMethod.GET, "/api/reportes/resumen")).isFalse();
    }

    @Test
    void rutasInternasNoSeExponen() {
        assertThat(RutasPublicas.esInterna("/api/productos/interno/precios")).isTrue();
        assertThat(RutasPublicas.esInterna("/api/inventario/reservas")).isTrue();
        assertThat(RutasPublicas.esInterna("/api/pedidos/interno/123/estado")).isTrue();
        assertThat(RutasPublicas.esInterna("/api/pedidos/mis-pedidos")).isFalse();
    }
}
