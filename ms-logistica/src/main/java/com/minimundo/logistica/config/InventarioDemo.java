package com.minimundo.logistica.config;

import com.minimundo.logistica.client.ServiceClient;
import com.minimundo.logistica.model.Inventario;
import com.minimundo.logistica.model.Movimiento;
import com.minimundo.logistica.model.TipoMovimiento;
import com.minimundo.logistica.repository.InventarioRepository;
import com.minimundo.logistica.repository.MovimientoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inventario inicial de las prendas con fotos que agrega {@code CatalogoFotos} en ms-catalogo (DEMO_DATA=true).
 * Consulta las variantes al catálogo (puede tardar en arrancar) y crea el inventario de los SKU que falten, solo para
 * los códigos de demostración: las prendas que crea el operario empiezan sin existencias, como dice el documento.
 */
@Component
@Order(2)
public class InventarioDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventarioDemo.class);

    static final Set<String> CODIGOS = Set.of("VES-003", "VES-004", "VES-005", "CON-002", "CON-003", "CON-004",
            "CON-005", "CON-006", "CON-007", "CON-008", "CON-009", "CON-010", "PIJ-003", "PIJ-004", "PIJ-005",
            "PIJ-006", "CAM-002", "CAM-003", "CAM-004", "CAM-005", "BLU-001", "PAN-002", "CHA-002", "CHA-003",
            "CHA-004", "CHA-005", "SAC-001", "SUE-001", "SUE-002", "SUE-003", "ENT-001", "BAL-001", "ZAP-001",
            "ZAP-002", "BOT-001", "TEN-002", "GOR-002");

    private final InventarioRepository inventario;
    private final MovimientoRepository movimientos;
    private final ServiceClient catalogo;
    private final boolean demo;

    public InventarioDemo(InventarioRepository inventario, MovimientoRepository movimientos,
                          @Qualifier("catalogoClient") ServiceClient catalogo,
                          @Value("${minimundo.demo-data:false}") boolean demo) {
        this.inventario = inventario;
        this.movimientos = movimientos;
        this.catalogo = catalogo;
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!demo) {
            return;
        }
        Thread.ofVirtual().name("inventario-demo").start(() -> {
            for (int intento = 0; intento < 30; intento++) {
                try {
                    if (completar()) {
                        return;
                    }
                } catch (RuntimeException e) {
                    log.debug("Catálogo aún no disponible para el inventario de demostración: {}", e.getMessage());
                }
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    /** Devuelve true cuando todos los códigos de demostración ya tienen inventario. */
    boolean completar() {
        List<Map<String, Object>> variantes = catalogo.get("/api/productos/interno/variantes",
                new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        int creados = 0;
        Set<String> vistos = new java.util.HashSet<>();
        for (Map<String, Object> v : variantes) {
            String codigo = String.valueOf(v.get("codigo"));
            if (!CODIGOS.contains(codigo)) {
                continue;
            }
            vistos.add(codigo);
            String sku = String.valueOf(v.get("sku"));
            if (inventario.findBySku(sku).isPresent()) {
                continue;
            }
            int stock = 4 + Math.floorMod(sku.hashCode(), 15);
            Inventario i = new Inventario();
            i.setSku(sku);
            i.setProductoNombre((String) v.get("productoNombre"));
            i.setTalla((String) v.get("talla"));
            i.setColor((String) v.get("color"));
            i.setStock(stock);
            i.setReservado(0);
            i.setDisponible(stock);
            i.setFechaActualizacion(Instant.now());
            inventario.save(i);
            movimientos.save(new Movimiento(sku, TipoMovimiento.ENTRADA, stock, stock, "Inventario inicial", null,
                    null, "Carga inicial"));
            creados++;
        }
        if (creados > 0) {
            log.info("Inventario de demostración: {} SKU nuevos", creados);
        }
        return vistos.containsAll(CODIGOS);
    }
}
