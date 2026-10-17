package com.minimundo.logistica.config;

import com.minimundo.logistica.model.Inventario;
import com.minimundo.logistica.model.Movimiento;
import com.minimundo.logistica.model.Parametros;
import com.minimundo.logistica.model.TipoMovimiento;
import com.minimundo.logistica.repository.InventarioRepository;
import com.minimundo.logistica.repository.MovimientoRepository;
import com.minimundo.logistica.repository.ParametrosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Inventario inicial de demostración (DEMO_DATA=true). Los SKU coinciden con el catálogo de ms-catalogo.
 * Incluye variantes con stock bajo y una agotada para mostrar las alertas (HU-30, HU-49).
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    record Semilla(String sku, String producto, String talla, String color, int stock) {
    }

    private static final List<Semilla> SEMILLAS = List.of(
            new Semilla("BOD-001-03M-ROSA", "Body manga larga de algodón", "0-3M", "Rosa", 12),
            new Semilla("BOD-001-03M-BLANCO", "Body manga larga de algodón", "0-3M", "Blanco", 8),
            new Semilla("BOD-001-03M-CELESTE", "Body manga larga de algodón", "0-3M", "Celeste", 15),
            new Semilla("BOD-001-36M-ROSA", "Body manga larga de algodón", "3-6M", "Rosa", 10),
            new Semilla("BOD-001-36M-BLANCO", "Body manga larga de algodón", "3-6M", "Blanco", 6),
            new Semilla("BOD-001-36M-CELESTE", "Body manga larga de algodón", "3-6M", "Celeste", 20),
            new Semilla("BOD-001-69M-ROSA", "Body manga larga de algodón", "6-9M", "Rosa", 9),
            new Semilla("BOD-001-69M-BLANCO", "Body manga larga de algodón", "6-9M", "Blanco", 12),
            new Semilla("BOD-001-69M-CELESTE", "Body manga larga de algodón", "6-9M", "Celeste", 8),
            new Semilla("VES-001-2-ROSA", "Vestido de tul con flores", "2", "Rosa", 12),
            new Semilla("VES-001-2-LILA", "Vestido de tul con flores", "2", "Lila", 8),
            new Semilla("VES-001-4-ROSA", "Vestido de tul con flores", "4", "Rosa", 15),
            new Semilla("VES-001-4-LILA", "Vestido de tul con flores", "4", "Lila", 10),
            new Semilla("CON-001-4-AZUL", "Conjunto deportivo de algodón", "4", "Azul", 12),
            new Semilla("CON-001-4-GRIS", "Conjunto deportivo de algodón", "4", "Gris", 8),
            new Semilla("CON-001-6-AZUL", "Conjunto deportivo de algodón", "6", "Azul", 15),
            new Semilla("CON-001-6-GRIS", "Conjunto deportivo de algodón", "6", "Gris", 10),
            new Semilla("PIJ-001-4-VERDE", "Pijama de dinosaurios", "4", "Verde", 12),
            new Semilla("PIJ-001-6-VERDE", "Pijama de dinosaurios", "6", "Verde", 8),
            new Semilla("PIJ-001-8-VERDE", "Pijama de dinosaurios", "8", "Verde", 15),
            new Semilla("CAM-001-8-BLANCO", "Camiseta estampada arcoíris", "8", "Blanco", 12),
            new Semilla("CAM-001-8-AMARILLO", "Camiseta estampada arcoíris", "8", "Amarillo", 8),
            new Semilla("CAM-001-10-BLANCO", "Camiseta estampada arcoíris", "10", "Blanco", 15),
            new Semilla("CAM-001-10-AMARILLO", "Camiseta estampada arcoíris", "10", "Amarillo", 10),
            new Semilla("PAN-001-8-AZUL", "Jean clásico ajustable", "8", "Azul", 12),
            new Semilla("PAN-001-10-AZUL", "Jean clásico ajustable", "10", "Azul", 8),
            new Semilla("PAN-001-12-AZUL", "Jean clásico ajustable", "12", "Azul", 15),
            new Semilla("CHA-001-10-ROJO", "Chaqueta impermeable con capota", "10", "Rojo", 12),
            new Semilla("CHA-001-10-AZUL", "Chaqueta impermeable con capota", "10", "Azul", 8),
            new Semilla("CHA-001-12-ROJO", "Chaqueta impermeable con capota", "12", "Rojo", 2),
            new Semilla("CHA-001-12-AZUL", "Chaqueta impermeable con capota", "12", "Azul", 10),
            new Semilla("TEN-001-26-BLANCO", "Tenis con luces", "26", "Blanco", 3),
            new Semilla("TEN-001-26-ROSA", "Tenis con luces", "26", "Rosa", 8),
            new Semilla("TEN-001-28-BLANCO", "Tenis con luces", "28", "Blanco", 15),
            new Semilla("TEN-001-28-ROSA", "Tenis con luces", "28", "Rosa", 10),
            new Semilla("TEN-001-30-BLANCO", "Tenis con luces", "30", "Blanco", 6),
            new Semilla("TEN-001-30-ROSA", "Tenis con luces", "30", "Rosa", 20),
            new Semilla("GOR-001-UNICA-BEIGE", "Gorro tejido con pompón", "UNICA", "Beige", 12),
            new Semilla("GOR-001-UNICA-ROSA", "Gorro tejido con pompón", "UNICA", "Rosa", 8),
            new Semilla("BOD-002-36M-LILA", "Body manga corta estampado", "3-6M", "Lila", 12),
            new Semilla("BOD-002-69M-LILA", "Body manga corta estampado", "6-9M", "Lila", 8),
            new Semilla("BOD-002-912M-LILA", "Body manga corta estampado", "9-12M", "Lila", 15),
            new Semilla("VES-002-4-ROJO", "Vestido navideño de terciopelo", "4", "Rojo", 12),
            new Semilla("VES-002-6-ROJO", "Vestido navideño de terciopelo", "6", "Rojo", 0),
            new Semilla("PIJ-002-69M-AMARILLO", "Pijama enteriza osito", "6-9M", "Amarillo", 12),
            new Semilla("PIJ-002-912M-AMARILLO", "Pijama enteriza osito", "9-12M", "Amarillo", 8)
    );

    private final InventarioRepository inventario;
    private final MovimientoRepository movimientos;
    private final ParametrosRepository parametros;
    private final boolean demo;

    public DataSeeder(InventarioRepository inventario, MovimientoRepository movimientos,
                      ParametrosRepository parametros, @Value("${minimundo.demo-data:false}") boolean demo) {
        this.inventario = inventario;
        this.movimientos = movimientos;
        this.parametros = parametros;
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!parametros.existsById(Parametros.ID)) {
            parametros.save(new Parametros());
        }
        if (!demo || inventario.count() > 0) {
            return;
        }
        for (Semilla s : SEMILLAS) {
            Inventario i = new Inventario();
            i.setSku(s.sku());
            i.setProductoNombre(s.producto());
            i.setTalla(s.talla());
            i.setColor(s.color());
            i.setStock(s.stock());
            i.setReservado(0);
            i.setDisponible(s.stock());
            i.setFechaActualizacion(Instant.now());
            inventario.save(i);
            if (s.stock() > 0) {
                movimientos.save(new Movimiento(s.sku(), TipoMovimiento.ENTRADA, s.stock(), s.stock(),
                        "Inventario inicial", null, null, "Carga inicial"));
            }
        }
        log.info("Inventario de demostración cargado: {} SKU", SEMILLAS.size());
    }
}
