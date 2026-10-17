package com.minimundo.pedidos.service;

import com.minimundo.pedidos.dto.PedidoDtos.PagoRequest;
import com.minimundo.pedidos.error.ApiException;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pasarela de pago simulada (HU-39, HU-40). No cobra dinero real.
 * Tarjetas de prueba: cualquier número válido se aprueba, excepto el terminado en 0002 (fondos insuficientes)
 * y el terminado en 0069 (tarjeta reportada).
 */
@Component
public class PagoSimulado {

    public record Resultado(boolean aprobado, String motivo, String referencia, String ultimos4) {
    }

    /** Valida el formato de la tarjeta antes de reservar stock (HU-39). */
    public void validar(PagoRequest pago) {
        String numero = limpiar(pago.numeroTarjeta());
        Map<String, String> errores = new LinkedHashMap<>();
        if (!numero.matches("[0-9]{13,19}")) {
            errores.put("pago.numeroTarjeta", "El número de tarjeta debe tener entre 13 y 19 dígitos");
        }
        if (pago.vencimiento() != null && pago.vencimiento().matches("(0[1-9]|1[0-2])/[0-9]{2}")) {
            String[] p = pago.vencimiento().split("/");
            YearMonth vence = YearMonth.of(2000 + Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            if (vence.isBefore(YearMonth.now(ZoneId.of("America/Bogota")))) {
                errores.put("pago.vencimiento", "La tarjeta está vencida");
            }
        }
        if (!errores.isEmpty()) {
            throw ApiException.badRequest("Revisa los datos de la tarjeta", errores);
        }
    }

    public Resultado procesar(PagoRequest pago) {
        validar(pago);
        String numero = limpiar(pago.numeroTarjeta());
        String ultimos4 = numero.substring(numero.length() - 4);
        if (numero.endsWith("0002")) {
            return new Resultado(false, "Fondos insuficientes", null, ultimos4);
        }
        if (numero.endsWith("0069")) {
            return new Resultado(false, "La entidad financiera rechazó la transacción", null, ultimos4);
        }
        String ref = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Resultado(true, null, ref, ultimos4);
    }

    private static String limpiar(String numero) {
        return numero == null ? "" : numero.replaceAll("[\\s-]", "");
    }
}
