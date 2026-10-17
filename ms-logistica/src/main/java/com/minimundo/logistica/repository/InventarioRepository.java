package com.minimundo.logistica.repository;

import com.minimundo.logistica.model.Inventario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InventarioRepository extends MongoRepository<Inventario, String> {

    Optional<Inventario> findBySku(String sku);

    List<Inventario> findBySkuIn(List<String> skus);
}
