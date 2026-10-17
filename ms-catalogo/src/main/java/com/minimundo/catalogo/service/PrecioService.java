package com.minimundo.catalogo.service;

import com.minimundo.catalogo.model.Categoria;
import com.minimundo.catalogo.model.Descuento;
import com.minimundo.catalogo.model.Producto;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * Precio vigente de un producto (HU-23, HU-26): aplica el mayor descuento vigente entre el del producto
 * y el de su categoría. Fuera de la vigencia se cobra el precio normal.
 */
@Service
public class PrecioService {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final Clock clock;

    public PrecioService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate hoy() {
        return LocalDate.now(clock.withZone(ZONA));
    }

    public int descuentoVigente(Producto p, Map<String, Categoria> categorias) {
        LocalDate hoy = hoy();
        int pct = 0;
        Descuento d = p.getDescuento();
        if (d != null && d.vigente(hoy)) {
            pct = d.getPorcentaje();
        }
        Categoria c = p.getCategoriaId() == null ? null : categorias.get(p.getCategoriaId());
        if (c != null && c.getDescuento() != null && c.getDescuento().vigente(hoy)) {
            pct = Math.max(pct, c.getDescuento().getPorcentaje());
        }
        return pct;
    }

    /** El precio mostrado es el original menos el porcentaje, redondeado al peso (HU-26). */
    public static long aplicar(long precio, int porcentaje) {
        if (porcentaje <= 0) {
            return precio;
        }
        return Math.round(precio * (100 - porcentaje) / 100.0);
    }
}
