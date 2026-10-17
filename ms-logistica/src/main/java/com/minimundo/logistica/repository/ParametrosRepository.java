package com.minimundo.logistica.repository;

import com.minimundo.logistica.model.Parametros;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ParametrosRepository extends MongoRepository<Parametros, String> {
}
