package com.minimundo.atencion.service;

import com.minimundo.atencion.client.ServiceClient;
import com.minimundo.atencion.dto.AtencionDtos.ItemPedido;
import com.minimundo.atencion.dto.AtencionDtos.PedidoInfo;
import com.minimundo.atencion.dto.AtencionDtos.SolicitudDto;
import com.minimundo.atencion.dto.AtencionDtos.SolicitudRequest;
import com.minimundo.atencion.dto.AtencionDtos.VarianteInfo;
import com.minimundo.atencion.error.ApiException;
import com.minimundo.atencion.model.EstadoSolicitud;
import com.minimundo.atencion.model.Historial;
import com.minimundo.atencion.model.Solicitud;
import com.minimundo.atencion.model.TipoSolicitud;
import com.minimundo.atencion.repository.SolicitudRepository;
import com.minimundo.atencion.security.AuthUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Solicitudes de cambio de talla (HU-64) y devolución (HU-65) solo dentro del plazo (HU-66), con decisión del asesor
 * (HU-72, HU-73), motivo visible para el cliente (HU-67) y marca de reingreso para logística (HU-59).
 */
@Service
public class SolicitudService {

    private final SolicitudRepository repo;
    private final MongoTemplate mongo;
    private final ParametrosService parametros;
    private final Consecutivos consecutivos;
    private final ServiceClient pedidos;
    private final ServiceClient catalogo;
    private final ServiceClient logistica;
    private final Clock clock;

    public SolicitudService(SolicitudRepository repo, MongoTemplate mongo, ParametrosService parametros,
                            Consecutivos consecutivos, @Qualifier("pedidosClient") ServiceClient pedidos,
                            @Qualifier("catalogoClient") ServiceClient catalogo,
                            @Qualifier("logisticaClient") ServiceClient logistica, Clock clock) {
        this.repo = repo;
        this.mongo = mongo;
        this.parametros = parametros;
        this.consecutivos = consecutivos;
        this.pedidos = pedidos;
        this.catalogo = catalogo;
        this.logistica = logistica;
        this.clock = clock;
    }

    public Solicitud obtener(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("La solicitud no existe"));
    }

    public SolicitudDto crear(SolicitudRequest req, AuthUser cliente) {
        TipoSolicitud tipo;
        try {
            tipo = TipoSolicitud.valueOf(req.tipo().trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Indica si es un cambio de talla o una devolución");
        }
        if (req.motivo() == null || req.motivo().isBlank()) {
            throw ApiException.badRequest("Indica el motivo de la solicitud", Map.of("motivo", "El motivo es obligatorio"));
        }
        PedidoInfo pedido;
        try {
            pedido = pedidos.get("/api/pedidos/interno/{id}", PedidoInfo.class, req.pedidoId());
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw ApiException.badRequest("El pedido seleccionado no existe");
            }
            throw e;
        }
        if (!cliente.id().equals(pedido.clienteId())) {
            throw ApiException.forbidden("Este pedido no te pertenece");
        }
        if (!"ENTREGADO".equals(pedido.estado())) {
            throw ApiException.conflict("Solo puedes solicitar cambios o devoluciones de pedidos entregados");
        }
        int plazo = parametros.obtener().getPlazoCambiosDias();
        if (pedido.fechaEntrega() == null
                || Instant.now(clock).isAfter(pedido.fechaEntrega().plus(Duration.ofDays(plazo)))) {
            throw ApiException.conflict("El plazo para cambios y devoluciones es de " + plazo
                    + " días desde la entrega y ya venció para este pedido");
        }
        String sku = req.sku().trim().toUpperCase(Locale.ROOT);
        ItemPedido item = pedido.items().stream().filter(i -> i.sku().equalsIgnoreCase(sku)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("El producto seleccionado no hace parte del pedido"));
        int cantidad = req.cantidad() == null ? 1 : req.cantidad();
        if (cantidad > item.cantidad()) {
            throw ApiException.badRequest("Compraste " + item.cantidad() + " unidad(es) de este producto");
        }
        if (repo.existsByPedidoIdAndSkuAndEstadoIn(pedido.id(), sku, List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA))) {
            throw ApiException.conflict("Ya tienes una solicitud en curso para este producto");
        }
        Solicitud s = new Solicitud();
        if (tipo == TipoSolicitud.CAMBIO) {
            if (req.tallaDeseada() == null || req.tallaDeseada().isBlank()) {
                throw ApiException.badRequest("Selecciona la talla que deseas", Map.of("tallaDeseada", "Obligatoria"));
            }
            if (req.tallaDeseada().trim().equalsIgnoreCase(item.talla())) {
                throw ApiException.badRequest("La talla deseada es la misma que compraste");
            }
            VarianteInfo deseada = varianteDeseada(item, req.tallaDeseada().trim());
            int disponible = disponibilidad(deseada.sku());
            if (disponible < cantidad) {
                throw ApiException.conflict("La talla " + deseada.talla() + " en color " + deseada.color()
                        + " está agotada en este momento. Puedes solicitar una devolución o intentar más tarde.");
            }
            s.setTallaDeseada(deseada.talla());
            s.setSkuDeseado(deseada.sku());
        }
        s.setNumero(consecutivos.siguiente("SOL"));
        s.setTipo(tipo);
        s.setClienteId(cliente.id());
        s.setClienteNombre(cliente.nombre());
        s.setPedidoId(pedido.id());
        s.setPedidoCodigo(pedido.codigo());
        s.setSku(item.sku());
        s.setProductoId(item.productoId());
        s.setNombreProducto(item.nombre());
        s.setTalla(item.talla());
        s.setColor(item.color());
        s.setCantidad(cantidad);
        s.setMotivo(req.motivo().trim());
        s.setEstado(EstadoSolicitud.PENDIENTE);
        s.setFechaSolicitud(Instant.now(clock));
        s.getHistorial().add(new Historial(EstadoSolicitud.PENDIENTE.name(), cliente.id(), cliente.nombre(),
                tipo.getTexto() + " solicitada"));
        return dto(repo.save(s));
    }

    public List<SolicitudDto> mias(String clienteId) {
        return repo.findByClienteIdOrderByFechaSolicitudDesc(clienteId).stream().map(this::dto).toList();
    }

    public List<SolicitudDto> listar(String estado, Boolean reingresada, String clienteId) {
        Query q = new Query();
        if (estado != null && !estado.isBlank()) {
            q.addCriteria(Criteria.where("estado").is(parseEstado(estado)));
        }
        if (reingresada != null) {
            q.addCriteria(Criteria.where("reingresada").is(reingresada));
        }
        if (clienteId != null && !clienteId.isBlank()) {
            q.addCriteria(Criteria.where("clienteId").is(clienteId));
        }
        q.with(Sort.by(Sort.Direction.ASC, "fechaSolicitud"));
        return mongo.find(q, Solicitud.class).stream().map(this::dto).toList();
    }

    public SolicitudDto detalle(String id, AuthUser usuario) {
        Solicitud s = obtener(id);
        if (usuario.tieneRol("CLIENTE") && !s.getClienteId().equals(usuario.id())) {
            throw ApiException.forbidden("Esta solicitud no te pertenece");
        }
        return dto(s);
    }

    /** HU-72 y HU-73: una solicitud ya decidida no se decide de nuevo; el rechazo exige motivo. */
    public SolicitudDto decidir(String id, boolean aprobar, String motivo, AuthUser asesor) {
        Solicitud s = obtener(id);
        if (s.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw ApiException.conflict("Esta solicitud ya fue " + s.getEstado().getTexto().toLowerCase());
        }
        if (!aprobar && (motivo == null || motivo.isBlank())) {
            throw ApiException.badRequest("Indica el motivo del rechazo", Map.of("motivo", "El motivo es obligatorio"));
        }
        s.setEstado(aprobar ? EstadoSolicitud.APROBADA : EstadoSolicitud.RECHAZADA);
        s.setMotivoRechazo(aprobar ? null : motivo.trim());
        s.setAsesorId(asesor.id());
        s.setAsesorNombre(asesor.nombre());
        s.setFechaDecision(Instant.now(clock));
        s.getHistorial().add(new Historial(s.getEstado().name(), asesor.id(), asesor.nombre(),
                aprobar ? "Aprobada: logística recibirá la mercancía" : "Rechazada: " + motivo.trim()));
        return dto(repo.save(s));
    }

    /** HU-59: logística marca la mercancía como reingresada (una sola vez). */
    public SolicitudDto marcarReingreso(String id, String usuarioNombre) {
        Solicitud s = obtener(id);
        if (s.getEstado() != EstadoSolicitud.APROBADA) {
            throw ApiException.conflict("Solo se reingresa mercancía de solicitudes aprobadas");
        }
        if (s.isReingresada()) {
            throw ApiException.conflict("La mercancía de esta solicitud ya fue reingresada");
        }
        s.setReingresada(true);
        s.setFechaReingreso(Instant.now(clock));
        s.setReingresadaPor(usuarioNombre);
        s.getHistorial().add(new Historial("REINGRESADA", null, usuarioNombre, "Mercancía reingresada al inventario"));
        return dto(repo.save(s));
    }

    private VarianteInfo varianteDeseada(ItemPedido item, String talla) {
        try {
            return catalogo.get("/api/productos/interno/variante?productoId={p}&talla={t}&color={c}",
                    VarianteInfo.class, item.productoId(), talla, item.color());
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw ApiException.badRequest("Este producto no se fabrica en talla " + talla + " color " + item.color());
            }
            throw e;
        }
    }

    private int disponibilidad(String sku) {
        Map<String, Integer> m = logistica.get("/api/inventario/disponibilidad?skus={s}",
                new ParameterizedTypeReference<Map<String, Integer>>() { }, sku);
        return m == null ? 0 : m.getOrDefault(sku, 0);
    }

    public SolicitudDto dto(Solicitud s) {
        return new SolicitudDto(s.getId(), s.getNumero(), s.getTipo().name(), s.getTipo().getTexto(), s.getClienteId(),
                s.getClienteNombre(), s.getPedidoId(), s.getPedidoCodigo(), s.getSku(), s.getProductoId(),
                s.getNombreProducto(), s.getTalla(), s.getColor(), s.getCantidad(), s.getTallaDeseada(), s.getMotivo(),
                s.getEstado().name(), s.getEstado().getTexto(), s.getMotivoRechazo(), s.getAsesorNombre(),
                s.getFechaSolicitud(), s.getFechaDecision(), s.isReingresada(), s.getFechaReingreso(), s.getHistorial());
    }

    static EstadoSolicitud parseEstado(String estado) {
        try {
            return EstadoSolicitud.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Estado no válido: " + estado);
        }
    }
}
