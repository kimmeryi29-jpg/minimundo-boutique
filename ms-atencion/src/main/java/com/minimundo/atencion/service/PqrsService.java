package com.minimundo.atencion.service;

import com.minimundo.atencion.client.ServiceClient;
import com.minimundo.atencion.dto.AtencionDtos.PedidoInfo;
import com.minimundo.atencion.dto.AtencionDtos.PqrsDto;
import com.minimundo.atencion.dto.AtencionDtos.RadicarPqrsRequest;
import com.minimundo.atencion.dto.AtencionDtos.ReportePqrs;
import com.minimundo.atencion.error.ApiException;
import com.minimundo.atencion.model.EstadoPqrs;
import com.minimundo.atencion.model.Historial;
import com.minimundo.atencion.model.Pqrs;
import com.minimundo.atencion.model.TipoPqrs;
import com.minimundo.atencion.repository.PqrsRepository;
import com.minimundo.atencion.security.AuthUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PQRS: radicación (HU-61, HU-62), consulta del cliente (HU-63), bandeja del asesor con filtros (HU-68),
 * asignación (HU-69), respuesta y cierre (HU-70), vencimiento por días hábiles (HU-74), historial (HU-75)
 * y reporte por tipo y estado (HU-85).
 */
@Service
public class PqrsService {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final PqrsRepository repo;
    private final MongoTemplate mongo;
    private final ParametrosService parametros;
    private final Consecutivos consecutivos;
    private final ServiceClient pedidos;
    private final Clock clock;

    public PqrsService(PqrsRepository repo, MongoTemplate mongo, ParametrosService parametros,
                       Consecutivos consecutivos, @Qualifier("pedidosClient") ServiceClient pedidos, Clock clock) {
        this.repo = repo;
        this.mongo = mongo;
        this.parametros = parametros;
        this.consecutivos = consecutivos;
        this.pedidos = pedidos;
        this.clock = clock;
    }

    public Pqrs obtener(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("La PQRS no existe"));
    }

    /** HU-61: queda RADICADA con número de radicado. HU-62: solo se asocia un pedido propio. */
    public PqrsDto radicar(RadicarPqrsRequest req, AuthUser cliente) {
        TipoPqrs tipo;
        try {
            tipo = TipoPqrs.valueOf(req.tipo().trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Selecciona un tipo válido: petición, queja, reclamo o sugerencia",
                    Map.of("tipo", "Tipo no válido"));
        }
        if (req.descripcion() == null || req.descripcion().isBlank()) {
            throw ApiException.badRequest("Describe tu caso", Map.of("descripcion", "La descripción es obligatoria"));
        }
        Pqrs p = new Pqrs();
        if (req.pedidoId() != null && !req.pedidoId().isBlank()) {
            PedidoInfo pedido = pedidoDe(req.pedidoId());
            if (!cliente.id().equals(pedido.clienteId())) {
                throw ApiException.forbidden("Solo puedes asociar tus propios pedidos");
            }
            p.setPedidoId(pedido.id());
            p.setPedidoCodigo(pedido.codigo());
        }
        p.setRadicado(consecutivos.siguiente("PQRS"));
        p.setTipo(tipo);
        p.setDescripcion(req.descripcion().trim());
        p.setClienteId(cliente.id());
        p.setClienteNombre(cliente.nombre());
        p.setClienteEmail(cliente.email());
        p.setEstado(EstadoPqrs.RADICADA);
        p.setFechaRadicacion(Instant.now(clock));
        p.getHistorial().add(new Historial(EstadoPqrs.RADICADA.name(), cliente.id(), cliente.nombre(),
                "Radicada por el cliente"));
        return dto(repo.save(p));
    }

    public List<PqrsDto> misPqrs(String clienteId) {
        return repo.findByClienteIdOrderByFechaRadicacionDesc(clienteId).stream().map(this::dto).toList();
    }

    public PqrsDto detalle(String id, AuthUser usuario) {
        Pqrs p = obtener(id);
        if (usuario.tieneRol("CLIENTE") && !p.getClienteId().equals(usuario.id())) {
            throw ApiException.forbidden("Esta PQRS no te pertenece");
        }
        return dto(p);
    }

    /** HU-68: filtros por estado y vencimiento. */
    public List<PqrsDto> bandeja(String estado, Boolean vencidas, String asesorId) {
        Query q = new Query();
        if (estado != null && !estado.isBlank()) {
            q.addCriteria(Criteria.where("estado").is(parseEstado(estado)));
        }
        if (asesorId != null && !asesorId.isBlank()) {
            q.addCriteria(Criteria.where("asesorId").is(asesorId));
        }
        q.with(Sort.by(Sort.Direction.ASC, "fechaRadicacion"));
        List<PqrsDto> lista = mongo.find(q, Pqrs.class).stream().map(this::dto).toList();
        if (vencidas != null) {
            lista = lista.stream().filter(p -> p.vencida() == vencidas).toList();
        }
        return lista;
    }

    /** HU-69: actualización atómica; si otro asesor ya la tomó no se puede tomar. */
    public PqrsDto tomar(String id, AuthUser asesor) {
        Pqrs actual = obtener(id);
        if (actual.getEstado() != EstadoPqrs.RADICADA) {
            throw ApiException.conflict(actual.getAsesorNombre() != null
                    ? "Esta PQRS ya está asignada a " + actual.getAsesorNombre()
                    : "Solo se pueden tomar PQRS en estado RADICADA");
        }
        Query q = new Query(Criteria.where("_id").is(id).and("estado").is(EstadoPqrs.RADICADA));
        Update u = new Update().set("estado", EstadoPqrs.EN_GESTION).set("asesorId", asesor.id())
                .set("asesorNombre", asesor.nombre()).set("fechaAsignacion", Instant.now(clock))
                .push("historial", new Historial(EstadoPqrs.EN_GESTION.name(), asesor.id(), asesor.nombre(),
                        "Asignada a " + asesor.nombre()));
        Pqrs p = mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), Pqrs.class);
        if (p == null) {
            throw ApiException.conflict("Otro asesor tomó esta PQRS hace un momento");
        }
        return dto(p);
    }

    /** HU-70: solo el asesor asignado la cierra y la respuesta no puede estar vacía. */
    public PqrsDto responder(String id, String respuesta, AuthUser asesor) {
        if (respuesta == null || respuesta.isBlank()) {
            throw ApiException.badRequest("Escribe la respuesta antes de cerrar la PQRS");
        }
        Pqrs p = obtener(id);
        if (p.getEstado() != EstadoPqrs.EN_GESTION) {
            throw ApiException.conflict("Solo se puede responder una PQRS en gestión");
        }
        if (!asesor.id().equals(p.getAsesorId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Esta PQRS está asignada a " + p.getAsesorNombre());
        }
        p.setRespuesta(respuesta.trim());
        p.setEstado(EstadoPqrs.CERRADA);
        p.setFechaCierre(Instant.now(clock));
        p.getHistorial().add(new Historial(EstadoPqrs.CERRADA.name(), asesor.id(), asesor.nombre(),
                "Respondida y cerrada"));
        return dto(repo.save(p));
    }

    /** HU-85. */
    public ReportePqrs reporte(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now(clock.withZone(ZONA));
        LocalDate ini = desde != null ? desde : hoy.withDayOfMonth(1);
        LocalDate fin = hasta != null ? hasta : hoy;
        if (fin.isBefore(ini)) {
            throw ApiException.badRequest("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        Query q = new Query(Criteria.where("fechaRadicacion").gte(ini.atStartOfDay(ZONA).toInstant())
                .lt(fin.plusDays(1).atStartOfDay(ZONA).toInstant()));
        List<PqrsDto> lista = mongo.find(q, Pqrs.class).stream().map(this::dto).toList();
        Map<String, Long> porTipo = new LinkedHashMap<>();
        for (TipoPqrs t : TipoPqrs.values()) {
            porTipo.put(t.getTexto(), lista.stream().filter(p -> p.tipo().equals(t.name())).count());
        }
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoPqrs e : EstadoPqrs.values()) {
            porEstado.put(e.getTexto(), lista.stream().filter(p -> p.estado().equals(e.name())).count());
        }
        long vencidas = lista.stream().filter(PqrsDto::vencida).count();
        return new ReportePqrs(ini.toString(), fin.toString(), lista.size(), porTipo, porEstado, vencidas,
                lista.isEmpty());
    }

    private PedidoInfo pedidoDe(String pedidoId) {
        try {
            return pedidos.get("/api/pedidos/interno/{id}", PedidoInfo.class, pedidoId);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw ApiException.badRequest("El pedido seleccionado no existe");
            }
            throw e;
        }
    }

    /** HU-74: vencida si supera los días hábiles configurados sin cerrarse; una CERRADA nunca se marca. */
    public PqrsDto dto(Pqrs p) {
        int limite = parametros.obtener().getDiasVencimientoPqrs();
        Instant hasta = p.getFechaCierre() != null ? p.getFechaCierre() : Instant.now(clock);
        long dias = DiasHabiles.entre(p.getFechaRadicacion(), hasta);
        boolean vencida = p.getEstado() != EstadoPqrs.CERRADA && dias > limite;
        return new PqrsDto(p.getId(), p.getRadicado(), p.getTipo().name(), p.getTipo().getTexto(), p.getDescripcion(),
                p.getClienteId(), p.getClienteNombre(), p.getClienteEmail(), p.getPedidoId(), p.getPedidoCodigo(),
                p.getEstado().name(), p.getEstado().getTexto(), p.getAsesorId(), p.getAsesorNombre(), p.getRespuesta(),
                p.getFechaRadicacion(), p.getFechaCierre(), vencida, dias, p.getHistorial());
    }

    static EstadoPqrs parseEstado(String estado) {
        try {
            return EstadoPqrs.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Estado no válido: " + estado);
        }
    }
}
