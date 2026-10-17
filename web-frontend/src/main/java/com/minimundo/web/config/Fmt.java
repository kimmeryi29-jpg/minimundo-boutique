package com.minimundo.web.config;

import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Formatos para las vistas: pesos colombianos, fechas en hora de Bogotá y etiquetas de estado. */
@Component("fmt")
public class Fmt {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", CO).withZone(ZONA);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", CO).withZone(ZONA);

    private static final Map<String, String> TEXTOS = Map.ofEntries(
            Map.entry("CREADO", "Pago pendiente"), Map.entry("PAGADO", "Pagado"),
            Map.entry("EN_ALISTAMIENTO", "En alistamiento"), Map.entry("DESPACHADO", "Despachado"),
            Map.entry("EN_RUTA", "En camino"), Map.entry("ENTREGADO", "Entregado"), Map.entry("CANCELADO", "Cancelado"),
            Map.entry("RADICADA", "Radicada"), Map.entry("EN_GESTION", "En gestión"), Map.entry("CERRADA", "Cerrada"),
            Map.entry("PENDIENTE", "Pendiente"), Map.entry("APROBADA", "Aprobada"), Map.entry("RECHAZADA", "Rechazada"),
            Map.entry("CLIENTE", "Cliente"), Map.entry("ASESOR", "Asesor de servicio al cliente"),
            Map.entry("OPERARIO", "Operario de catálogo e inventario"), Map.entry("REPARTIDOR", "Repartidor"),
            Map.entry("ADMIN", "Administrador"), Map.entry("ENTRADA", "Entrada"), Map.entry("SALIDA", "Salida"),
            Map.entry("AJUSTE", "Ajuste"), Map.entry("BORRADOR", "Borrador"), Map.entry("PUBLICADO", "Publicado"),
            Map.entry("FALLIDO", "Fallido"));

    private static final Map<String, String> CLASES = Map.ofEntries(
            Map.entry("CREADO", "estado-gris"), Map.entry("PAGADO", "estado-azul"),
            Map.entry("EN_ALISTAMIENTO", "estado-amarillo"), Map.entry("DESPACHADO", "estado-morado"),
            Map.entry("EN_RUTA", "estado-menta"), Map.entry("ENTREGADO", "estado-verde"),
            Map.entry("CANCELADO", "estado-rojo"), Map.entry("RADICADA", "estado-azul"),
            Map.entry("EN_GESTION", "estado-amarillo"), Map.entry("CERRADA", "estado-verde"),
            Map.entry("PENDIENTE", "estado-amarillo"), Map.entry("APROBADA", "estado-verde"),
            Map.entry("RECHAZADA", "estado-rojo"), Map.entry("BORRADOR", "estado-gris"),
            Map.entry("PUBLICADO", "estado-verde"), Map.entry("FALLIDO", "estado-rojo"));

    public String pesos(Object valor) {
        if (valor == null) {
            return "—";
        }
        long v = valor instanceof Number n ? n.longValue() : Long.parseLong(valor.toString());
        NumberFormat f = NumberFormat.getIntegerInstance(CO);
        return "$" + f.format(v);
    }

    public String fecha(Object valor) {
        Instant i = instante(valor);
        return i == null ? "—" : FECHA.format(i);
    }

    public String fechaHora(Object valor) {
        Instant i = instante(valor);
        return i == null ? "—" : FECHA_HORA.format(i);
    }

    public String dia(Object valor) {
        if (valor == null) {
            return "—";
        }
        return LocalDate.parse(valor.toString().substring(0, 10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", CO));
    }

    public String estado(Object estado) {
        return estado == null ? "" : TEXTOS.getOrDefault(estado.toString(), estado.toString());
    }

    public String clase(Object estado) {
        return estado == null ? "estado-gris" : CLASES.getOrDefault(estado.toString(), "estado-gris");
    }

    public String hoy() {
        return LocalDate.now(ZONA).toString();
    }

    public String inicioMes() {
        return LocalDate.now(ZONA).withDayOfMonth(1).toString();
    }

    private Instant instante(Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return null;
        }
        String s = valor.toString();
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
