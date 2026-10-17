package com.minimundo.catalogo.repository;

import com.minimundo.catalogo.model.EstadoProducto;
import com.minimundo.catalogo.model.Producto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends MongoRepository<Producto, String> {

    boolean existsByCodigo(String codigo);

    long countByCodigoStartingWith(String prefijo);

    boolean existsByVariantesSku(String sku);

    Optional<Producto> findByVariantesSku(String sku);

    List<Producto> findByVariantesSkuIn(List<String> skus);

    boolean existsByCategoriaId(String categoriaId);

    boolean existsByColeccionIdsContains(String coleccionId);

    List<Producto> findByEstadoAndActivoTrue(EstadoProducto estado);
}
