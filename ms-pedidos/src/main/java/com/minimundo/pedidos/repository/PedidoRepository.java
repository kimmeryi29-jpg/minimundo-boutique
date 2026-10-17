package com.minimundo.pedidos.repository;

import com.minimundo.pedidos.model.EstadoPedido;
import com.minimundo.pedidos.model.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    List<Pedido> findByClienteIdAndEstadoNotOrderByFechaCreacionDesc(String clienteId, EstadoPedido estado);

    Optional<Pedido> findFirstByClienteIdAndEstadoOrderByFechaCreacionDesc(String clienteId, EstadoPedido estado);

    Optional<Pedido> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
