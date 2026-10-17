package com.minimundo.usuarios.service;

import com.minimundo.usuarios.model.EventoAuditoria;
import com.minimundo.usuarios.model.TipoEvento;
import com.minimundo.usuarios.repository.AuditoriaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

/** Registro y consulta de eventos de seguridad (HU-15). */
@Service
public class AuditoriaService {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final AuditoriaRepository repo;
    private final MongoTemplate mongo;

    public AuditoriaService(AuditoriaRepository repo, MongoTemplate mongo) {
        this.repo = repo;
        this.mongo = mongo;
    }

    public void registrar(TipoEvento tipo, String email, String usuarioId, String resultado, String detalle,
                          String actor) {
        repo.save(new EventoAuditoria(tipo, email, usuarioId, resultado, detalle, actor));
    }

    public List<EventoAuditoria> consultar(LocalDate desde, LocalDate hasta, String usuario, String tipo) {
        Query q = new Query();
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
        if (usuario != null && !usuario.isBlank()) {
            q.addCriteria(Criteria.where("email").regex(Pattern.quote(usuario.trim().toLowerCase()), "i"));
        }
        if (tipo != null && !tipo.isBlank()) {
            q.addCriteria(Criteria.where("tipo").is(tipo));
        }
        q.with(Sort.by(Sort.Direction.DESC, "fecha")).limit(500);
        return mongo.find(q, EventoAuditoria.class);
    }

    public static Instant ahora() {
        return Instant.now();
    }
}
