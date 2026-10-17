package com.minimundo.pedidos.repository;

import com.minimundo.pedidos.model.Carrito;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CarritoRepository extends MongoRepository<Carrito, String> {

    Optional<Carrito> findByClienteId(String clienteId);
}
