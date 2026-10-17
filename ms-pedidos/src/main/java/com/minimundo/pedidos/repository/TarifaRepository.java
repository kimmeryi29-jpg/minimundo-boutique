package com.minimundo.pedidos.repository;

import com.minimundo.pedidos.model.TarifaEnvio;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TarifaRepository extends MongoRepository<TarifaEnvio, String> {

    Optional<TarifaEnvio> findByCiudadNormalizada(String ciudadNormalizada);

    boolean existsByCiudadNormalizada(String ciudadNormalizada);

    List<TarifaEnvio> findAllByOrderByCiudadAsc();
}
