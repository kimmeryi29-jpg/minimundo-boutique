package com.minimundo.catalogo.repository;

import com.minimundo.catalogo.model.Categoria;
import com.minimundo.catalogo.model.TipoCategoria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CategoriaRepository extends MongoRepository<Categoria, String> {

    boolean existsByNombreNormalizado(String nombreNormalizado);

    List<Categoria> findByTipoOrderByNombreAsc(TipoCategoria tipo);

    List<Categoria> findAllByOrderByTipoAscNombreAsc();
}
