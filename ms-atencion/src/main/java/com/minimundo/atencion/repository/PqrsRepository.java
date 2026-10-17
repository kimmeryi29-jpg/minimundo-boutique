package com.minimundo.atencion.repository;

import com.minimundo.atencion.model.Pqrs;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PqrsRepository extends MongoRepository<Pqrs, String> {

    List<Pqrs> findByClienteIdOrderByFechaRadicacionDesc(String clienteId);
}
