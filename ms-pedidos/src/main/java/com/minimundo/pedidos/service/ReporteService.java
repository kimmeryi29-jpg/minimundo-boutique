package com.minimundo.pedidos.service;

import com.minimundo.pedidos.dto.PedidoDtos.ConteoEstado;
import com.minimundo.pedidos.dto.PedidoDtos.MasVendido;
import com.minimundo.pedidos.dto.PedidoDtos.ProductoActivo;
import com.minimundo.pedidos.dto.PedidoDtos.ReporteVentas;
import com.minimundo.pedidos.dto.PedidoDtos.Resumen;
import com.minimundo.pedidos.dto.PedidoDtos.SinVentas;
import com.minimundo.pedidos.dto.PedidoDtos.VentaFila;
import com.minimundo.pedidos.error.ApiException;
import com.minimundo.pedidos.model.EstadoPedido;
import com.minimundo.pedidos.model.ItemPedido;
import com.minimundo.pedidos.model.Pedido;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indicadores del negocio: ventas del periodo (HU-45, HU-76), pedidos por estado (HU-77), más vendidos (HU-78),
 * filtro por fechas (HU-79), productos sin ventas (HU-80), exportación CSV (HU-86), tiempo promedio entre pago
 * y despacho (HU-88) y periodo sin datos (HU-90). Los pedidos CANCELADOS no cuentan como ventas.
 */
@Service
public class ReporteService {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONA);

    private final MongoTemplate mongo;
    private final Integraciones integraciones;
    private final Clock clock;

    public ReporteService(MongoTemplate mongo, Integraciones integraciones, Clock clock) {
        this.mongo = mongo;
        this.integraciones = integraciones;
        this.clock = clock;
    }

    /** Periodo por defecto: el mes actual (HU-76). HU-79: fin anterior al inicio → error. */
    public LocalDate[] periodo(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now(clock.withZone(ZONA));
        LocalDate ini = desde != null ? desde : hoy.withDayOfMonth(1);
        LocalDate fin = hasta != null ? hasta : hoy;
        if (fin.isBefore(ini)) {
            throw ApiException.badRequest("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        return new LocalDate[]{ini, fin};
    }

    public Resumen resumen(LocalDate desde, LocalDate hasta) {
        LocalDate[] p = periodo(desde, hasta);
        Instant ini = p[0].atStartOfDay(ZONA).toInstant();
        Instant fin = p[1].plusDays(1).atStartOfDay(ZONA).toInstant();

        List<Pedido> creados = mongo.find(new Query(Criteria.where("fechaCreacion").gte(ini).lt(fin)), Pedido.class);
        Map<EstadoPedido, Long> conteo = new EnumMap<>(EstadoPedido.class);
        for (EstadoPedido e : EstadoPedido.values()) {
            conteo.put(e, 0L);
        }
        creados.forEach(x -> conteo.merge(x.getEstado(), 1L, Long::sum));
        List<ConteoEstado> porEstado = conteo.entrySet().stream()
                .map(e -> new ConteoEstado(e.getKey().name(), e.getKey().getTexto(), e.getValue())).toList();

        List<Pedido> ventas = ventas(ini, fin);
        long total = ventas.stream().mapToLong(Pedido::getTotal).sum();

        Map<String, long[]> acumulado = new HashMap<>();
        Map<String, String> nombres = new HashMap<>();
        for (Pedido v : ventas) {
            for (ItemPedido i : v.getItems()) {
                long[] a = acumulado.computeIfAbsent(i.getProductoId(), k -> new long[2]);
                a[0] += i.getCantidad();
                a[1] += i.subtotal();
                nombres.putIfAbsent(i.getProductoId(), i.getNombre());
            }
        }
        List<MasVendido> masVendidos = acumulado.entrySet().stream()
                .map(e -> new MasVendido(e.getKey(), nombres.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(MasVendido::unidades).reversed())
                .limit(5).toList();

        List<Pedido> despachados = mongo.find(new Query(Criteria.where("fechaDespacho").gte(ini).lt(fin)
                .and("fechaPago").ne(null)), Pedido.class);
        Double promedio = null;
        if (!despachados.isEmpty()) {
            promedio = despachados.stream()
                    .mapToDouble(x -> Duration.between(x.getFechaPago(), x.getFechaDespacho()).toMinutes() / 60.0)
                    .average().orElse(0);
            promedio = Math.round(promedio * 10) / 10.0;
        }
        boolean sinDatos = creados.isEmpty() && ventas.isEmpty();
        return new Resumen(p[0].toString(), p[1].toString(), total, ventas.size(), creados.size(), porEstado,
                masVendidos, promedio, despachados.size(), sinDatos);
    }

    /** HU-45: total y número de pedidos pagados del periodo. */
    public ReporteVentas ventas(LocalDate desde, LocalDate hasta) {
        LocalDate[] p = periodo(desde, hasta);
        List<Pedido> ventas = ventas(p[0].atStartOfDay(ZONA).toInstant(), p[1].plusDays(1).atStartOfDay(ZONA).toInstant());
        List<VentaFila> filas = ventas.stream().map(v -> new VentaFila(v.getCodigo(), v.getClienteNombre(),
                v.getFechaPago(), v.getEstado().getTexto(), v.getItems().stream().mapToInt(ItemPedido::getCantidad).sum(),
                v.getSubtotal(), v.getDescuentos(), v.getEnvio(), v.getTotal())).toList();
        return new ReporteVentas(p[0].toString(), p[1].toString(), ventas.stream().mapToLong(Pedido::getTotal).sum(),
                ventas.size(), filas);
    }

    /** HU-86: CSV con los mismos filtros; sin datos no se genera un archivo vacío. */
    public String ventasCsv(LocalDate desde, LocalDate hasta) {
        ReporteVentas r = ventas(desde, hasta);
        if (r.pedidos().isEmpty()) {
            throw ApiException.notFound("No hay datos para el periodo seleccionado");
        }
        StringBuilder sb = new StringBuilder("codigo,cliente,fecha_pago,estado,unidades,subtotal,descuentos,envio,total\n");
        for (VentaFila f : r.pedidos()) {
            sb.append(campo(f.codigo())).append(',').append(campo(f.cliente())).append(',')
                    .append(f.fechaPago() == null ? "" : FECHA.format(f.fechaPago())).append(',')
                    .append(campo(f.estado())).append(',').append(f.unidades()).append(',').append(f.subtotal())
                    .append(',').append(f.descuentos()).append(',').append(f.envio()).append(',').append(f.total())
                    .append('\n');
        }
        sb.append("TOTAL,,,,,,,,").append(r.total()).append('\n');
        return sb.toString();
    }

    /** HU-80: productos activos sin ventas en los últimos N días, con su última fecha de venta. */
    public List<SinVentas> sinVentas(int dias) {
        if (dias <= 0) {
            throw ApiException.badRequest("Los días deben ser un número mayor que cero");
        }
        Instant limite = Instant.now(clock).minus(Duration.ofDays(dias));
        List<ProductoActivo> activos = integraciones.productosActivos();
        Map<String, Instant> ultimaVenta = new LinkedHashMap<>();
        Query q = new Query(Criteria.where("estado").nin(EstadoPedido.CREADO, EstadoPedido.CANCELADO));
        q.with(Sort.by(Sort.Direction.ASC, "fechaPago"));
        for (Pedido v : mongo.find(q, Pedido.class)) {
            for (ItemPedido i : v.getItems()) {
                if (v.getFechaPago() != null) {
                    ultimaVenta.merge(i.getProductoId(), v.getFechaPago(), (a, b) -> a.isAfter(b) ? a : b);
                }
            }
        }
        List<SinVentas> res = new ArrayList<>();
        for (ProductoActivo p : activos) {
            Instant ultima = ultimaVenta.get(p.id());
            boolean sinVentaReciente = ultima == null ? p.fechaPublicacion() == null || p.fechaPublicacion().isBefore(limite)
                    : ultima.isBefore(limite);
            if (sinVentaReciente) {
                Instant ref = ultima != null ? ultima : p.fechaPublicacion();
                long diasSin = ref == null ? dias : Duration.between(ref, Instant.now(clock)).toDays();
                res.add(new SinVentas(p.id(), p.codigo(), p.nombre(), ultima, diasSin));
            }
        }
        res.sort(Comparator.comparingLong(SinVentas::diasSinVenta).reversed());
        return res;
    }

    private List<Pedido> ventas(Instant ini, Instant fin) {
        Query q = new Query(Criteria.where("fechaPago").gte(ini).lt(fin)
                .and("estado").nin(EstadoPedido.CREADO, EstadoPedido.CANCELADO));
        q.with(Sort.by(Sort.Direction.DESC, "fechaPago"));
        return mongo.find(q, Pedido.class);
    }

    private static String campo(String s) {
        if (s == null) {
            return "";
        }
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
