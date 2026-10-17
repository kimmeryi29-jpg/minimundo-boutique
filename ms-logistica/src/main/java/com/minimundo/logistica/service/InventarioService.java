package com.minimundo.logistica.service;

import com.minimundo.logistica.client.ServiceClient;
import com.minimundo.logistica.dto.LogisticaDtos.AjusteRequest;
import com.minimundo.logistica.dto.LogisticaDtos.EntradaRequest;
import com.minimundo.logistica.dto.LogisticaDtos.FaltanteStock;
import com.minimundo.logistica.dto.LogisticaDtos.InventarioDto;
import com.minimundo.logistica.dto.LogisticaDtos.InventarioListado;
import com.minimundo.logistica.dto.LogisticaDtos.ItemReservaRequest;
import com.minimundo.logistica.dto.LogisticaDtos.ReingresoMarca;
import com.minimundo.logistica.dto.LogisticaDtos.ReservaRequest;
import com.minimundo.logistica.dto.LogisticaDtos.SolicitudInfo;
import com.minimundo.logistica.dto.LogisticaDtos.VarianteCatalogo;
import com.minimundo.logistica.error.ApiException;
import com.minimundo.logistica.model.Inventario;
import com.minimundo.logistica.model.ItemReserva;
import com.minimundo.logistica.model.Movimiento;
import com.minimundo.logistica.model.Parametros;
import com.minimundo.logistica.model.Reserva;
import com.minimundo.logistica.model.TipoMovimiento;
import com.minimundo.logistica.repository.InventarioRepository;
import com.minimundo.logistica.repository.MovimientoRepository;
import com.minimundo.logistica.repository.ParametrosRepository;
import com.minimundo.logistica.repository.ReservaRepository;
import com.minimundo.logistica.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inventario por SKU: entradas (HU-46), consulta (HU-47), ajustes (HU-48), alertas (HU-49), reserva atómica
 * y descuento al despachar (HU-54), liberación (HU-55), historial de movimientos (HU-56), reingreso de
 * devoluciones (HU-59) y filtros de stock bajo o agotado (HU-60).
 */
@Service
public class InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioService.class);
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final InventarioRepository repo;
    private final MovimientoRepository movimientos;
    private final ReservaRepository reservas;
    private final ParametrosRepository parametros;
    private final MongoTemplate mongo;
    private final ServiceClient catalogo;
    private final ServiceClient atencion;

    public InventarioService(InventarioRepository repo, MovimientoRepository movimientos, ReservaRepository reservas,
                             ParametrosRepository parametros, MongoTemplate mongo,
                             @Qualifier("catalogoClient") ServiceClient catalogo,
                             @Qualifier("atencionClient") ServiceClient atencion) {
        this.repo = repo;
        this.movimientos = movimientos;
        this.reservas = reservas;
        this.parametros = parametros;
        this.mongo = mongo;
        this.catalogo = catalogo;
        this.atencion = atencion;
    }

    // ------------------------------------------------------------------ Parámetros

    public Parametros parametros() {
        return parametros.findById(Parametros.ID).orElseGet(Parametros::new);
    }

    /** HU-82: el mínimo por defecto lo usan las variantes que no tienen mínimo propio. */
    public Parametros actualizarParametros(int stockMinimoDefecto) {
        if (stockMinimoDefecto < 0) {
            throw ApiException.badRequest("El stock mínimo debe ser un entero mayor o igual a cero");
        }
        Parametros p = parametros();
        p.setStockMinimoDefecto(stockMinimoDefecto);
        return parametros.save(p);
    }

    // ------------------------------------------------------------------ Consulta

    /** HU-47, HU-49, HU-60. Las variantes sin entradas aparecen con stock 0. */
    public InventarioListado listar(String sku, String talla, String color, String filtro) {
        int minimoDefecto = parametros().getStockMinimoDefecto();
        Map<String, Inventario> docs = repo.findAll().stream()
                .collect(Collectors.toMap(Inventario::getSku, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<InventarioDto> todos = new ArrayList<>();
        boolean catalogoOk = true;
        try {
            List<VarianteCatalogo> variantes = catalogo.get("/api/productos/interno/variantes",
                    new ParameterizedTypeReference<List<VarianteCatalogo>>() { });
            for (VarianteCatalogo v : variantes) {
                Inventario inv = docs.remove(v.sku());
                todos.add(dto(inv, v.sku(), v.productoId(), v.productoNombre(), v.talla(), v.color(), minimoDefecto));
            }
        } catch (ApiException e) {
            catalogoOk = false;
            log.warn("Catálogo no disponible; se muestra solo el inventario registrado");
        }
        for (Inventario inv : docs.values()) {
            todos.add(dto(inv, inv.getSku(), null, inv.getProductoNombre(), inv.getTalla(), inv.getColor(),
                    minimoDefecto));
        }
        int alertas = (int) todos.stream().filter(InventarioDto::bajo).count();
        List<InventarioDto> filtrados = todos.stream()
                .filter(i -> contiene(i.sku(), sku))
                .filter(i -> igual(i.talla(), talla))
                .filter(i -> igual(i.color(), color))
                .filter(i -> filtro == null || filtro.isBlank()
                        || ("bajo".equalsIgnoreCase(filtro) && i.bajo())
                        || ("agotado".equalsIgnoreCase(filtro) && i.agotado()))
                .sorted(Comparator.comparing(InventarioDto::productoNombre, Comparator.nullsLast(String::compareTo))
                        .thenComparing(InventarioDto::sku))
                .toList();
        return new InventarioListado(filtrados, alertas, catalogoOk);
    }

    /** HU-49: variantes con disponible por debajo del mínimo. */
    public List<InventarioDto> alertas() {
        return listar(null, null, null, "bajo").items();
    }

    /** Disponibilidad pública por SKU para el detalle del producto y el carrito (HU-29, HU-30, HU-35). */
    public Map<String, Integer> disponibilidad(List<String> skus) {
        Map<String, Integer> res = new LinkedHashMap<>();
        List<String> normalizados = skus.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        normalizados.forEach(s -> res.put(s, 0));
        repo.findBySkuIn(normalizados).forEach(i -> res.put(i.getSku(), Math.max(0, i.getDisponible())));
        return res;
    }

    public List<Movimiento> movimientos(String sku, String tipo, LocalDate desde, LocalDate hasta) {
        Query q = new Query(Criteria.where("sku").is(sku.toUpperCase(Locale.ROOT)));
        if (tipo != null && !tipo.isBlank()) {
            q.addCriteria(Criteria.where("tipo").is(TipoMovimiento.valueOf(tipo.toUpperCase(Locale.ROOT))));
        }
        if (desde != null || hasta != null) {
            Criteria c = Criteria.where("fecha");
            if (desde != null) {
                c = c.gte(desde.atStartOfDay(ZONA).toInstant());
            }
            if (hasta != null) {
                c = c.lt(hasta.plusDays(1).atStartOfDay(ZONA).toInstant());
            }
            q.addCriteria(c);
        }
        q.with(Sort.by(Sort.Direction.DESC, "fecha"));
        return mongo.find(q, Movimiento.class);
    }

    // ------------------------------------------------------------------ Entradas y ajustes

    /** HU-46: un SKU inexistente o una cantidad menor o igual a cero se rechaza. */
    public InventarioDto registrarEntrada(EntradaRequest req, AuthUser usuario) {
        if (req.cantidad() == null || req.cantidad() <= 0) {
            throw ApiException.badRequest("La cantidad debe ser mayor que cero");
        }
        VarianteCatalogo v = varianteDelCatalogo(req.sku());
        Inventario inv = incrementar(v, req.cantidad());
        movimientos.save(new Movimiento(v.sku(), TipoMovimiento.ENTRADA, req.cantidad(), inv.getStock(),
                blancoPorDefecto(req.motivo(), "Entrada de mercancía"), null, usuario.id(), usuario.nombre()));
        return dto(inv, v.sku(), v.productoId(), v.productoNombre(), v.talla(), v.color(),
                parametros().getStockMinimoDefecto());
    }

    /** HU-48: el ajuste no puede dejar el stock negativo ni por debajo de lo reservado. */
    public InventarioDto registrarAjuste(AjusteRequest req, AuthUser usuario) {
        if (req.motivo() == null || req.motivo().isBlank()) {
            throw ApiException.badRequest("Indica el motivo del ajuste");
        }
        if (req.cantidad() == null || req.cantidad() == 0) {
            throw ApiException.badRequest("La cantidad del ajuste no puede ser cero");
        }
        VarianteCatalogo v = varianteDelCatalogo(req.sku());
        int cantidad = req.cantidad();
        Inventario inv;
        if (cantidad > 0) {
            inv = incrementar(v, cantidad);
        } else {
            Query q = new Query(Criteria.where("sku").is(v.sku()).and("disponible").gte(-cantidad));
            Update u = new Update().inc("stock", cantidad).inc("disponible", cantidad)
                    .set("fechaActualizacion", Instant.now());
            inv = mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), Inventario.class);
            if (inv == null) {
                throw ApiException.conflict("El ajuste dejaría el stock negativo o por debajo de las unidades "
                        + "reservadas para pedidos");
            }
        }
        movimientos.save(new Movimiento(v.sku(), TipoMovimiento.AJUSTE, cantidad, inv.getStock(),
                req.motivo().trim(), null, usuario.id(), usuario.nombre()));
        return dto(inv, v.sku(), v.productoId(), v.productoNombre(), v.talla(), v.color(),
                parametros().getStockMinimoDefecto());
    }

    public InventarioDto definirMinimo(String sku, Integer minimo) {
        if (minimo != null && minimo < 0) {
            throw ApiException.badRequest("El mínimo debe ser un entero mayor o igual a cero");
        }
        VarianteCatalogo v = varianteDelCatalogo(sku);
        Query q = new Query(Criteria.where("sku").is(v.sku()));
        Update u = new Update().set("stockMinimo", minimo).set("fechaActualizacion", Instant.now())
                .setOnInsert("productoNombre", v.productoNombre()).setOnInsert("talla", v.talla())
                .setOnInsert("color", v.color()).setOnInsert("stock", 0).setOnInsert("reservado", 0)
                .setOnInsert("disponible", 0);
        Inventario inv = mongo.findAndModify(q, u, FindAndModifyOptions.options().upsert(true).returnNew(true),
                Inventario.class);
        return dto(inv, v.sku(), v.productoId(), v.productoNombre(), v.talla(), v.color(),
                parametros().getStockMinimoDefecto());
    }

    /** HU-59: solo se reingresa una devolución APROBADA y una sola vez. */
    public InventarioDto reingresarDevolucion(String solicitudId, AuthUser usuario) {
        SolicitudInfo s = atencion.get("/api/devoluciones/interno/{id}", SolicitudInfo.class, solicitudId);
        if (!"APROBADA".equals(s.estado())) {
            throw ApiException.conflict("Solo se puede reingresar mercancía de una solicitud aprobada");
        }
        if (s.reingresada()) {
            throw ApiException.conflict("La mercancía de la solicitud " + s.numero() + " ya fue reingresada");
        }
        VarianteCatalogo v = varianteDelCatalogo(s.sku());
        int cantidad = Math.max(1, s.cantidad());
        atencion.patch("/api/devoluciones/interno/{id}/reingreso", new ReingresoMarca(usuario.nombre()),
                Object.class, solicitudId);
        Inventario inv = incrementar(v, cantidad);
        movimientos.save(new Movimiento(v.sku(), TipoMovimiento.ENTRADA, cantidad, inv.getStock(),
                "Reingreso de devolución " + s.numero(), s.pedidoCodigo(), usuario.id(), usuario.nombre()));
        return dto(inv, v.sku(), v.productoId(), v.productoNombre(), v.talla(), v.color(),
                parametros().getStockMinimoDefecto());
    }

    // ------------------------------------------------------------------ Reservas (HU-54, HU-55)

    /**
     * Reserva todo o nada. Cada ítem se reserva con findAndModify condicionado a disponible ≥ cantidad, de modo
     * que si dos clientes compran la última unidad al mismo tiempo solo uno lo logra.
     */
    public Reserva reservar(ReservaRequest req) {
        reservas.findByPedidoId(req.pedidoId()).ifPresent(r -> {
            if (r.getEstado() == Reserva.Estado.ACTIVA) {
                liberarItems(r.getItems());
            }
            reservas.delete(r);
        });
        Map<String, ItemReservaRequest> agrupados = new LinkedHashMap<>();
        for (ItemReservaRequest i : req.items()) {
            String sku = i.sku().toUpperCase(Locale.ROOT);
            ItemReservaRequest previo = agrupados.get(sku);
            int cantidad = i.cantidad() + (previo == null ? 0 : previo.cantidad());
            agrupados.put(sku, new ItemReservaRequest(sku, i.nombre(), i.talla(), i.color(), cantidad));
        }
        List<ItemReserva> reservados = new ArrayList<>();
        List<FaltanteStock> faltantes = new ArrayList<>();
        for (ItemReservaRequest i : agrupados.values()) {
            Query q = new Query(Criteria.where("sku").is(i.sku()).and("disponible").gte(i.cantidad()));
            Update u = new Update().inc("reservado", i.cantidad()).inc("disponible", -i.cantidad())
                    .set("fechaActualizacion", Instant.now());
            Inventario inv = mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true),
                    Inventario.class);
            if (inv == null) {
                int disp = repo.findBySku(i.sku()).map(Inventario::getDisponible).orElse(0);
                faltantes.add(new FaltanteStock(i.sku(), i.nombre(), i.talla(), i.color(), i.cantidad(),
                        Math.max(0, disp)));
            } else {
                reservados.add(new ItemReserva(i.sku(), i.nombre(), i.talla(), i.color(), i.cantidad()));
            }
        }
        if (!faltantes.isEmpty()) {
            liberarItems(reservados);
            String detalle = faltantes.stream()
                    .map(f -> (f.nombre() == null ? f.sku() : f.nombre()) + " talla " + f.talla() + " color " + f.color()
                            + " (disponibles: " + f.disponible() + ")")
                    .collect(Collectors.joining("; "));
            throw new ApiException(HttpStatus.CONFLICT, "No hay stock suficiente de: " + detalle);
        }
        Reserva r = new Reserva();
        r.setPedidoId(req.pedidoId());
        r.setPedidoCodigo(req.pedidoCodigo());
        r.setItems(reservados);
        r.setEstado(Reserva.Estado.ACTIVA);
        r.setFecha(Instant.now());
        return reservas.save(r);
    }

    /** HU-55: acción compensatoria. Es idempotente: liberar dos veces no descuadra el inventario. */
    public void liberar(String pedidoId) {
        reservas.findByPedidoId(pedidoId).ifPresent(r -> {
            if (r.getEstado() == Reserva.Estado.ACTIVA) {
                liberarItems(r.getItems());
                r.setEstado(Reserva.Estado.LIBERADA);
                r.setFechaCierre(Instant.now());
                reservas.save(r);
            }
        });
    }

    /** HU-54: al despachar se descuentan stock y reservado; el disponible no cambia. */
    public void consumirReserva(String pedidoId, String pedidoCodigo, AuthUser usuario) {
        Reserva r = reservas.findByPedidoId(pedidoId)
                .orElseThrow(() -> ApiException.conflict("El pedido no tiene stock reservado"));
        if (r.getEstado() != Reserva.Estado.ACTIVA) {
            throw ApiException.conflict("La reserva del pedido ya no está activa");
        }
        List<ItemReserva> consumidos = new ArrayList<>();
        for (ItemReserva i : r.getItems()) {
            Query q = new Query(Criteria.where("sku").is(i.getSku()).and("reservado").gte(i.getCantidad())
                    .and("stock").gte(i.getCantidad()));
            Update u = new Update().inc("stock", -i.getCantidad()).inc("reservado", -i.getCantidad())
                    .set("fechaActualizacion", Instant.now());
            Inventario inv = mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true),
                    Inventario.class);
            if (inv == null) {
                revertirConsumo(consumidos);
                throw ApiException.conflict("Inconsistencia de inventario en " + i.getSku());
            }
            consumidos.add(i);
            movimientos.save(new Movimiento(i.getSku(), TipoMovimiento.SALIDA, -i.getCantidad(), inv.getStock(),
                    "Despacho del pedido " + pedidoCodigo, pedidoCodigo, usuario.id(), usuario.nombre()));
        }
        r.setEstado(Reserva.Estado.CONSUMIDA);
        r.setFechaCierre(Instant.now());
        reservas.save(r);
    }

    /** Deshace un consumo cuando falla la actualización del pedido (compensación). */
    public void revertirConsumoDePedido(String pedidoId) {
        reservas.findByPedidoId(pedidoId).ifPresent(r -> {
            if (r.getEstado() == Reserva.Estado.CONSUMIDA) {
                revertirConsumo(r.getItems());
                r.getItems().forEach(i -> movimientos.save(new Movimiento(i.getSku(), TipoMovimiento.AJUSTE,
                        i.getCantidad(), repo.findBySku(i.getSku()).map(Inventario::getStock).orElse(0),
                        "Reversión automática de despacho", r.getPedidoCodigo(), null, "Sistema")));
                r.setEstado(Reserva.Estado.ACTIVA);
                r.setFechaCierre(null);
                reservas.save(r);
            }
        });
    }

    private void revertirConsumo(List<ItemReserva> items) {
        for (ItemReserva i : items) {
            mongo.updateFirst(new Query(Criteria.where("sku").is(i.getSku())),
                    new Update().inc("stock", i.getCantidad()).inc("reservado", i.getCantidad()), Inventario.class);
        }
    }

    private void liberarItems(List<ItemReserva> items) {
        for (ItemReserva i : items) {
            mongo.updateFirst(new Query(Criteria.where("sku").is(i.getSku())),
                    new Update().inc("reservado", -i.getCantidad()).inc("disponible", i.getCantidad())
                            .set("fechaActualizacion", Instant.now()), Inventario.class);
        }
    }

    // ------------------------------------------------------------------ Exportación (HU-86)

    public String csv() {
        List<InventarioDto> items = listar(null, null, null, null).items();
        if (items.isEmpty()) {
            throw ApiException.notFound("No hay datos de inventario para exportar");
        }
        StringBuilder sb = new StringBuilder("sku,producto,talla,color,stock,reservado,disponible,stock_minimo,alerta\n");
        for (InventarioDto i : items) {
            sb.append(Csv.campo(i.sku())).append(',').append(Csv.campo(i.productoNombre())).append(',')
                    .append(Csv.campo(i.talla())).append(',').append(Csv.campo(i.color())).append(',')
                    .append(i.stock()).append(',').append(i.reservado()).append(',').append(i.disponible())
                    .append(',').append(i.stockMinimo()).append(',')
                    .append(i.agotado() ? "AGOTADO" : i.bajo() ? "BAJO" : "").append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Apoyo

    private VarianteCatalogo varianteDelCatalogo(String sku) {
        if (sku == null || sku.isBlank()) {
            throw ApiException.badRequest("El SKU es obligatorio");
        }
        try {
            return catalogo.get("/api/productos/interno/sku/{sku}", VarianteCatalogo.class,
                    sku.trim().toUpperCase(Locale.ROOT));
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw ApiException.badRequest("El SKU " + sku.trim().toUpperCase(Locale.ROOT)
                        + " no existe en el catálogo");
            }
            throw e;
        }
    }

    private Inventario incrementar(VarianteCatalogo v, int cantidad) {
        Query q = new Query(Criteria.where("sku").is(v.sku()));
        Update u = new Update().inc("stock", cantidad).inc("disponible", cantidad)
                .set("productoNombre", v.productoNombre()).set("talla", v.talla()).set("color", v.color())
                .set("fechaActualizacion", Instant.now()).setOnInsert("reservado", 0);
        return mongo.findAndModify(q, u, FindAndModifyOptions.options().upsert(true).returnNew(true),
                Inventario.class);
    }

    private InventarioDto dto(Inventario inv, String sku, String productoId, String nombre, String talla,
                              String color, int minimoDefecto) {
        int stock = inv == null ? 0 : inv.getStock();
        int reservado = inv == null ? 0 : inv.getReservado();
        int disponible = inv == null ? 0 : inv.getDisponible();
        boolean propio = inv != null && inv.getStockMinimo() != null;
        int minimo = propio ? inv.getStockMinimo() : minimoDefecto;
        return new InventarioDto(sku, productoId, nombre, talla, color, stock, reservado, disponible, minimo, propio,
                disponible < minimo, disponible <= 0, inv == null ? null : inv.getFechaActualizacion());
    }

    private static boolean contiene(String valor, String filtro) {
        return filtro == null || filtro.isBlank()
                || (valor != null && valor.toUpperCase(Locale.ROOT).contains(filtro.trim().toUpperCase(Locale.ROOT)));
    }

    private static boolean igual(String valor, String filtro) {
        return filtro == null || filtro.isBlank() || (valor != null && valor.equalsIgnoreCase(filtro.trim()));
    }

    private static String blancoPorDefecto(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor.trim();
    }
}
