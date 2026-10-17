package com.minimundo.logistica.repository;

import com.minimundo.logistica.model.Reserva;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ReservaRepository extends MongoRepository<Reserva, String> {

    Optional<Reserva> findByPedidoId(String pedidoId);
}
