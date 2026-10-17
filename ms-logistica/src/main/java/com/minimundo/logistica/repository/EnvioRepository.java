package com.minimundo.logistica.repository;

import com.minimundo.logistica.model.Envio;
import com.minimundo.logistica.model.EstadoEnvio;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EnvioRepository extends MongoRepository<Envio, String> {

    Optional<Envio> findByPedidoId(String pedidoId);

    List<Envio> findByRepartidorIdAndEstadoInOrderByFechaAsignacionAsc(String repartidorId, List<EstadoEnvio> estados);

    List<Envio> findByEventosUsuarioId(String usuarioId);
}
