package com.minimundo.atencion.repository;

import com.minimundo.atencion.model.EstadoSolicitud;
import com.minimundo.atencion.model.Solicitud;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SolicitudRepository extends MongoRepository<Solicitud, String> {

    List<Solicitud> findByClienteIdOrderByFechaSolicitudDesc(String clienteId);

    boolean existsByPedidoIdAndSkuAndEstadoIn(String pedidoId, String sku, List<EstadoSolicitud> estados);
}
