package com.minimundo.atencion.repository;

import com.minimundo.atencion.model.Parametros;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ParametrosRepository extends MongoRepository<Parametros, String> {
}
