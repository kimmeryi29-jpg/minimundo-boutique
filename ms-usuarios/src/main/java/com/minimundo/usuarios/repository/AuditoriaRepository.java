package com.minimundo.usuarios.repository;

import com.minimundo.usuarios.model.EventoAuditoria;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditoriaRepository extends MongoRepository<EventoAuditoria, String> {
}
