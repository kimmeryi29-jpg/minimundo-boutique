package com.minimundo.logistica.repository;

import com.minimundo.logistica.model.Movimiento;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MovimientoRepository extends MongoRepository<Movimiento, String> {
}
