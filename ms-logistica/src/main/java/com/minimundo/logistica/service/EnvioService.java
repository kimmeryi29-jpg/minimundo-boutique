package com.minimundo.logistica.service;

import com.minimundo.logistica.client.ServiceClient;
import com.minimundo.logistica.dto.LogisticaDtos.CambioEstadoPedido;
import com.minimundo.logistica.dto.LogisticaDtos.HistorialEntrega;
import com.minimundo.logistica.dto.LogisticaDtos.ItemPedido;
import com.minimundo.logistica.dto.LogisticaDtos.PedidoInfo;
import com.minimundo.logistica.dto.LogisticaDtos.UsuarioInfo;
import com.minimundo.logistica.error.ApiException;
import com.minimundo.logistica.model.Envio;
import com.minimundo.logistica.model.EstadoEnvio;
import com.minimundo.logistica.model.EventoEntrega;
import com.minimundo.logistica.model.ItemEnvio;
import com.minimundo.logistica.repository.EnvioRepository;
import com.minimundo.logistica.security.AuthUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alistamiento (HU-51, HU-52), despacho con guía (HU-53, HU-54, HU-57), asignación de repartidor (HU-106) y
 * entregas del repartidor (HU-58, HU-107 a HU-110, HU-112). Cada cambio se refleja en el estado del pedido
 * de ms-pedidos; si ms-pedidos no responde, el cambio no se aplica (respuesta 503 controlada, HU-103).
 */
@Service
public class EnvioService {

    public static final Map<String, String> MOTIVOS_FALLIDA = Map.of(
            "CLIENTE_AUSENTE", "Cliente ausente",
            "DIRECCION_ERRONEA", "Dirección errónea",
            "OTRO", "Otro motivo");

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final EnvioRepository repo;
    private final MongoTemplate mongo;
    private final InventarioService inventario;
    private final ServiceClient pedidos;
    private final ServiceClient usuarios;

    public EnvioService(EnvioRepository repo, MongoTemplate mongo, InventarioService inventario,
                        @Qualifier("pedidosClient") ServiceClient pedidos,
                        @Qualifier("usuariosClient") ServiceClient usuarios) {
        this.repo = repo;
        this.mongo = mongo;
        this.inventario = inventario;
        this.pedidos = pedidos;
        this.usuarios = usuarios;
    }

    public Envio obtener(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("El envío no existe"));
    }

    public Envio porPedido(String pedidoId) {
        return repo.findByPedidoId(pedidoId).orElseThrow(() -> ApiException.notFound("El pedido no tiene envío"));
    }

    /** Listado para el operario: por estado y entregas fallidas pendientes de reasignar (HU-109). */
    public List<Envio> listar(String estado, Boolean fallidas, String repartidorId) {
        Query q = new Query();
        if (estado != null && !estado.isBlank()) {
            q.addCriteria(Criteria.where("estado").is(parseEstado(estado)));
        }
        if (Boolean.TRUE.equals(fallidas)) {
            q.addCriteria(Criteria.where("entregaFallida").is(true).and("estado").is(EstadoEnvio.DESPACHADO));
        }
        if (repartidorId != null && !repartidorId.isBlank()) {
            q.addCriteria(Criteria.where("repartidorId").is(repartidorId));
        }
        q.with(Sort.by(Sort.Direction.ASC, "fechaPago"));
        return mongo.find(q, Envio.class);
    }

    // ------------------------------------------------------------------ Alistamiento

    /** HU-51: el pedido PAGADO pasa a EN_ALISTAMIENTO y queda asignado al operario que lo toma. */
    public Envio tomar(String pedidoId, AuthUser operario) {
        if (repo.findByPedidoId(pedidoId).isPresent()) {
            throw ApiException.conflict("Este pedido ya fue tomado por otro operario");
        }
        PedidoInfo p = pedidos.get("/api/pedidos/interno/{id}", PedidoInfo.class, pedidoId);
        if (!"PAGADO".equals(p.estado())) {
            throw ApiException.conflict("Solo se pueden tomar pedidos en estado PAGADO (estado actual: "
                    + p.estado() + ")");
        }
        Envio e = new Envio();
        e.setPedidoId(p.id());
        e.setPedidoCodigo(p.codigo());
        e.setClienteId(p.clienteId());
        e.setClienteNombre(p.clienteNombre());
        e.setClienteEmail(p.clienteEmail());
        e.setDireccion(p.direccionEnvio());
        e.setFechaPago(p.fechaPago());
        List<ItemEnvio> items = new ArrayList<>();
        for (ItemPedido i : p.items()) {
            ItemEnvio ie = new ItemEnvio();
            ie.setSku(i.sku());
            ie.setNombre(i.nombre());
            ie.setTalla(i.talla());
            ie.setColor(i.color());
            ie.setCantidad(i.cantidad());
            items.add(ie);
        }
        e.setItems(items);
        e.setEstado(EstadoEnvio.EN_ALISTAMIENTO);
        e.setOperarioId(operario.id());
        e.setOperarioNombre(operario.nombre());
        e.setFechaToma(Instant.now());
        Envio guardado;
        try {
            guardado = repo.save(e);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("Este pedido ya fue tomado por otro operario");
        }
        try {
            actualizarPedido(guardado, "EN_ALISTAMIENTO", operario, "Tomado por " + operario.nombre(), null);
        } catch (RuntimeException ex) {
            repo.delete(guardado);
            throw ex;
        }
        return guardado;
    }

    /** HU-52: registra la verificación de un ítem (SKU y cantidad deben coincidir con el pedido). */
    public Envio verificarItem(String envioId, String sku, Integer cantidad, AuthUser operario) {
        Envio e = obtener(envioId);
        exigirEstado(e, EstadoEnvio.EN_ALISTAMIENTO, "verificar ítems");
        exigirOperarioAsignado(e, operario);
        String buscado = sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
        ItemEnvio item = e.getItems().stream().filter(i -> i.getSku().equalsIgnoreCase(buscado)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("El SKU " + buscado + " no hace parte de este pedido"));
        if (cantidad == null || cantidad != item.getCantidad()) {
            throw ApiException.badRequest("La cantidad no coincide: el pedido tiene " + item.getCantidad()
                    + " unidad(es) de " + item.getSku());
        }
        item.setVerificado(true);
        return repo.save(e);
    }

    /** HU-52: solo se confirma cuando todos los ítems están verificados. */
    public Envio confirmarAlistamiento(String envioId, AuthUser operario) {
        Envio e = obtener(envioId);
        exigirEstado(e, EstadoEnvio.EN_ALISTAMIENTO, "confirmar el alistamiento");
        exigirOperarioAsignado(e, operario);
        long pendientes = e.getItems().stream().filter(i -> !i.isVerificado()).count();
        if (pendientes > 0) {
            throw ApiException.conflict("Faltan " + pendientes + " ítem(s) por verificar antes de confirmar el alistamiento");
        }
        e.setAlistamientoConfirmado(true);
        e.setFechaAlistamiento(Instant.now());
        return repo.save(e);
    }

    // ------------------------------------------------------------------ Despacho

    /**
     * HU-53: con transportadora y guía el pedido pasa a DESPACHADO. HU-57: si no está en alistamiento se responde 409
     * y no cambian ni el pedido ni el inventario. HU-54: se descuentan stock y reservado.
     */
    public Envio despachar(String envioId, String transportadora, String guia, AuthUser operario) {
        if (transportadora == null || transportadora.isBlank() || guia == null || guia.isBlank()) {
            throw ApiException.badRequest("La transportadora y el número de guía son obligatorios");
        }
        Envio e = obtener(envioId);
        if (e.getEstado() != EstadoEnvio.EN_ALISTAMIENTO) {
            throw ApiException.conflict("No se puede despachar: el pedido no está en alistamiento (estado actual: "
                    + e.getEstado() + ")");
        }
        if (!e.isAlistamientoConfirmado()) {
            throw ApiException.conflict("Primero confirma el alistamiento verificando todos los ítems");
        }
        inventario.consumirReserva(e.getPedidoId(), e.getPedidoCodigo(), operario);
        try {
            actualizarPedido(e, "DESPACHADO", operario, transportadora.trim() + " · guía " + guia.trim(),
                    new String[]{transportadora.trim(), guia.trim()});
        } catch (RuntimeException ex) {
            inventario.revertirConsumoDePedido(e.getPedidoId());
            throw ex;
        }
        e.setTransportadora(transportadora.trim());
        e.setGuia(guia.trim());
        e.setFechaDespacho(Instant.now());
        e.setEstado(EstadoEnvio.DESPACHADO);
        return repo.save(e);
    }

    // ------------------------------------------------------------------ Repartidor

    /** HU-106: solo pedidos DESPACHADO y solo repartidores activos. También sirve para reasignar tras una falla. */
    public Envio asignarRepartidor(String envioId, String repartidorId, AuthUser operario) {
        Envio e = obtener(envioId);
        if (e.getEstado() != EstadoEnvio.DESPACHADO) {
            throw ApiException.conflict("Solo se puede asignar un repartidor a un pedido DESPACHADO");
        }
        UsuarioInfo r;
        try {
            r = usuarios.get("/api/usuarios/{id}", UsuarioInfo.class, repartidorId);
        } catch (ApiException ex) {
            if (ex.getStatus().value() == 404) {
                throw ApiException.badRequest("El repartidor seleccionado no existe");
            }
            throw ex;
        }
        if (!"REPARTIDOR".equals(r.rol()) || !r.activo()) {
            throw ApiException.badRequest("El usuario seleccionado no es un repartidor activo");
        }
        e.setRepartidorId(r.id());
        e.setRepartidorNombre(r.nombre());
        e.setFechaAsignacion(Instant.now());
        e.setAsignadoPor(operario.nombre());
        e.getEventos().add(new EventoEntrega(EventoEntrega.Tipo.ASIGNADO, operario.id(), operario.nombre(), null,
                "Asignado a " + r.nombre()));
        return repo.save(e);
    }

    /** HU-107: solo los pedidos asignados al repartidor autenticado. */
    public List<Envio> misEntregas(AuthUser repartidor) {
        return repo.findByRepartidorIdAndEstadoInOrderByFechaAsignacionAsc(repartidor.id(),
                List.of(EstadoEnvio.DESPACHADO, EstadoEnvio.EN_RUTA));
    }

    /** HU-108: DESPACHADO → EN_RUTA. */
    public Envio marcarEnRuta(String envioId, AuthUser repartidor) {
        Envio e = obtener(envioId);
        exigirRepartidor(e, repartidor);
        if (e.getEstado() != EstadoEnvio.DESPACHADO) {
            throw ApiException.conflict("Solo un pedido DESPACHADO se puede marcar como en camino");
        }
        actualizarPedido(e, "EN_RUTA", repartidor, "En camino con " + repartidor.nombre(), null);
        e.setEstado(EstadoEnvio.EN_RUTA);
        e.setFechaEnRuta(Instant.now());
        e.setEntregaFallida(false);
        e.getEventos().add(new EventoEntrega(EventoEntrega.Tipo.EN_RUTA, repartidor.id(), repartidor.nombre(), null,
                null));
        return repo.save(e);
    }

    /** HU-58: EN_RUTA → ENTREGADO con fecha, hora y usuario. */
    public Envio registrarEntrega(String envioId, AuthUser repartidor) {
        Envio e = obtener(envioId);
        exigirRepartidor(e, repartidor);
        if (e.getEstado() != EstadoEnvio.EN_RUTA) {
            throw ApiException.conflict("Solo se puede registrar la entrega de un pedido EN_RUTA");
        }
        actualizarPedido(e, "ENTREGADO", repartidor, "Entregado por " + repartidor.nombre(), null);
        e.setEstado(EstadoEnvio.ENTREGADO);
        e.setFechaEntrega(Instant.now());
        e.getEventos().add(new EventoEntrega(EventoEntrega.Tipo.ENTREGADO, repartidor.id(), repartidor.nombre(), null,
                null));
        return repo.save(e);
    }

    /** HU-109: EN_RUTA → DESPACHADO, el intento queda registrado y el operario lo ve para reasignar. */
    public Envio reportarFallida(String envioId, String motivo, String detalle, AuthUser repartidor) {
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.badRequest("Selecciona el motivo de la entrega fallida");
        }
        String clave = motivo.trim().toUpperCase(Locale.ROOT);
        String texto = MOTIVOS_FALLIDA.get(clave);
        if (texto == null) {
            throw ApiException.badRequest("Motivo no válido. Usa CLIENTE_AUSENTE, DIRECCION_ERRONEA u OTRO");
        }
        if ("OTRO".equals(clave) && (detalle == null || detalle.isBlank())) {
            throw ApiException.badRequest("Describe el motivo de la entrega fallida");
        }
        Envio e = obtener(envioId);
        exigirRepartidor(e, repartidor);
        if (e.getEstado() != EstadoEnvio.EN_RUTA) {
            throw ApiException.conflict("Solo se puede reportar una entrega fallida de un pedido EN_RUTA");
        }
        String descripcion = texto + (detalle == null || detalle.isBlank() ? "" : ": " + detalle.trim());
        actualizarPedido(e, "DESPACHADO", repartidor, "Entrega fallida · " + descripcion, null);
        e.setEstado(EstadoEnvio.DESPACHADO);
        e.setEntregaFallida(true);
        e.setUltimoMotivoFallido(descripcion);
        e.getEventos().add(new EventoEntrega(EventoEntrega.Tipo.FALLIDO, repartidor.id(), repartidor.nombre(), clave,
                detalle == null ? null : detalle.trim()));
        return repo.save(e);
    }

    /** HU-110: entregas realizadas y fallidas del repartidor, filtrables por rango de fechas. */
    public List<HistorialEntrega> historial(AuthUser repartidor, LocalDate desde, LocalDate hasta) {
        Instant ini = desde == null ? null : desde.atStartOfDay(ZONA).toInstant();
        Instant fin = hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
        List<HistorialEntrega> res = new ArrayList<>();
        for (Envio e : repo.findByEventosUsuarioId(repartidor.id())) {
            for (EventoEntrega ev : e.getEventos()) {
                boolean propio = repartidor.id().equals(ev.getUsuarioId());
                boolean resultado = ev.getTipo() == EventoEntrega.Tipo.ENTREGADO || ev.getTipo() == EventoEntrega.Tipo.FALLIDO;
                if (!propio || !resultado) {
                    continue;
                }
                if ((ini != null && ev.getFecha().isBefore(ini)) || (fin != null && !ev.getFecha().isBefore(fin))) {
                    continue;
                }
                String motivo = ev.getMotivo() == null ? null : MOTIVOS_FALLIDA.getOrDefault(ev.getMotivo(), ev.getMotivo());
                res.add(new HistorialEntrega(e.getId(), e.getPedidoId(), e.getPedidoCodigo(), e.getClienteNombre(),
                        e.getDireccion() == null ? null : e.getDireccion().getDireccion(),
                        e.getDireccion() == null ? null : e.getDireccion().getCiudad(), ev.getFecha(),
                        ev.getTipo() == EventoEntrega.Tipo.ENTREGADO ? "ENTREGADO" : "FALLIDO", motivo,
                        ev.getDetalle()));
            }
        }
        res.sort(Comparator.comparing(HistorialEntrega::fecha).reversed());
        return res;
    }

    /** Cancelación de un pedido en alistamiento (HU-44): el envío queda CANCELADO. */
    public void cancelarPorPedido(String pedidoId) {
        repo.findByPedidoId(pedidoId).ifPresent(e -> {
            if (e.getEstado() != EstadoEnvio.EN_ALISTAMIENTO) {
                throw ApiException.conflict("El pedido ya fue despachado y no puede cancelarse");
            }
            e.setEstado(EstadoEnvio.CANCELADO);
            repo.save(e);
        });
    }

    // ------------------------------------------------------------------ Apoyo

    /** HU-112: el repartidor solo gestiona sus pedidos; sin repartidor asignado no se puede continuar. */
    private void exigirRepartidor(Envio e, AuthUser repartidor) {
        if (e.getRepartidorId() == null) {
            throw ApiException.conflict("El pedido no tiene un repartidor asignado");
        }
        if (!e.getRepartidorId().equals(repartidor.id())) {
            throw ApiException.forbidden("Este pedido está asignado a otro repartidor");
        }
    }

    private void exigirOperarioAsignado(Envio e, AuthUser operario) {
        if (operario.tieneRol("OPERARIO") && e.getOperarioId() != null && !e.getOperarioId().equals(operario.id())) {
            throw ApiException.forbidden("Este pedido lo está alistando " + e.getOperarioNombre());
        }
    }

    private void exigirEstado(Envio e, EstadoEnvio estado, String accion) {
        if (e.getEstado() != estado) {
            throw ApiException.conflict("No se puede " + accion + ": el pedido está en estado " + e.getEstado());
        }
    }

    private void actualizarPedido(Envio e, String estado, AuthUser usuario, String detalle, String[] guia) {
        CambioEstadoPedido cambio = new CambioEstadoPedido(estado, usuario.id(), usuario.nombre(), detalle,
                guia == null ? null : guia[0], guia == null ? null : guia[1],
                "EN_RUTA".equals(estado) ? usuario.nombre() : null);
        pedidos.patch("/api/pedidos/interno/{id}/estado", cambio, Object.class, e.getPedidoId());
    }

    static EstadoEnvio parseEstado(String estado) {
        try {
            return EstadoEnvio.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.badRequest("Estado no válido: " + estado);
        }
    }
}
