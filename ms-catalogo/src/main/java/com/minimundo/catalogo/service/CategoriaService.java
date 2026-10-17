package com.minimundo.catalogo.service;

import com.minimundo.catalogo.dto.CatalogoDtos.CategoriaDto;
import com.minimundo.catalogo.dto.CatalogoDtos.CategoriaRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.DescuentoDto;
import com.minimundo.catalogo.dto.CatalogoDtos.DescuentoRequest;
import com.minimundo.catalogo.error.ApiException;
import com.minimundo.catalogo.model.Categoria;
import com.minimundo.catalogo.model.Descuento;
import com.minimundo.catalogo.model.TipoCategoria;
import com.minimundo.catalogo.repository.CategoriaRepository;
import com.minimundo.catalogo.repository.ProductoRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** HU-21: categorías y colecciones (edad, temporada). HU-23: descuentos por categoría. */
@Service
public class CategoriaService {

    private final CategoriaRepository repo;
    private final ProductoRepository productos;
    private final MongoTemplate mongo;
    private final PrecioService precios;

    public CategoriaService(CategoriaRepository repo, ProductoRepository productos, MongoTemplate mongo,
                            PrecioService precios) {
        this.repo = repo;
        this.productos = productos;
        this.mongo = mongo;
        this.precios = precios;
    }

    public Map<String, Categoria> mapa() {
        return repo.findAll().stream().collect(Collectors.toMap(Categoria::getId, Function.identity()));
    }

    public Categoria obtener(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("La categoría o colección no existe"));
    }

    public List<CategoriaDto> listar(String tipo) {
        List<Categoria> lista = (tipo == null || tipo.isBlank())
                ? repo.findAllByOrderByTipoAscNombreAsc()
                : repo.findByTipoOrderByNombreAsc(parseTipo(tipo));
        return lista.stream().map(this::dto).toList();
    }

    public CategoriaDto crear(CategoriaRequest req) {
        String normalizado = Textos.normalizar(req.nombre());
        if (repo.existsByNombreNormalizado(normalizado)) {
            throw ApiException.conflict("Ya existe una categoría o colección con el nombre \"" + req.nombre().trim() + "\"");
        }
        Categoria c = new Categoria();
        c.setNombre(Textos.capitalizar(req.nombre()));
        c.setNombreNormalizado(normalizado);
        c.setTipo(parseTipo(req.tipo()));
        c.setDescripcion(req.descripcion());
        c.setFechaCreacion(Instant.now());
        return dto(repo.save(c));
    }

    /** Como los productos guardan el id, el nuevo nombre se refleja de inmediato en los productos asociados. */
    public CategoriaDto actualizar(String id, CategoriaRequest req) {
        Categoria c = obtener(id);
        String normalizado = Textos.normalizar(req.nombre());
        if (!normalizado.equals(c.getNombreNormalizado()) && repo.existsByNombreNormalizado(normalizado)) {
            throw ApiException.conflict("Ya existe una categoría o colección con el nombre \"" + req.nombre().trim() + "\"");
        }
        TipoCategoria nuevoTipo = parseTipo(req.tipo());
        if (nuevoTipo != c.getTipo() && tieneProductos(c)) {
            throw ApiException.conflict("No se puede cambiar el tipo porque tiene productos asociados");
        }
        c.setNombre(Textos.capitalizar(req.nombre()));
        c.setNombreNormalizado(normalizado);
        c.setTipo(nuevoTipo);
        c.setDescripcion(req.descripcion());
        return dto(repo.save(c));
    }

    /** HU-21: no se elimina una categoría con productos asignados. */
    public void eliminar(String id) {
        Categoria c = obtener(id);
        if (tieneProductos(c)) {
            throw ApiException.conflict("No se puede eliminar \"" + c.getNombre() + "\" porque tiene productos asociados");
        }
        repo.delete(c);
    }

    public CategoriaDto ponerDescuento(String id, DescuentoRequest req) {
        validarDescuento(req);
        Categoria c = obtener(id);
        c.setDescuento(new Descuento(req.porcentaje(), req.inicio(), req.fin()));
        return dto(repo.save(c));
    }

    public CategoriaDto quitarDescuento(String id) {
        Categoria c = obtener(id);
        c.setDescuento(null);
        return dto(repo.save(c));
    }

    /** HU-23: fecha de fin anterior a la de inicio o porcentaje fuera de 1–100 se rechaza. */
    public static void validarDescuento(DescuentoRequest req) {
        if (req.porcentaje() < 1 || req.porcentaje() > 100) {
            throw ApiException.badRequest("El porcentaje debe estar entre 1 y 100",
                    Map.of("porcentaje", "El porcentaje debe estar entre 1 y 100"));
        }
        if (req.fin().isBefore(req.inicio())) {
            throw ApiException.badRequest("La fecha de fin no puede ser anterior a la fecha de inicio",
                    Map.of("fin", "La fecha de fin no puede ser anterior a la de inicio"));
        }
    }

    private boolean tieneProductos(Categoria c) {
        return c.getTipo() == TipoCategoria.CATEGORIA
                ? productos.existsByCategoriaId(c.getId())
                : productos.existsByColeccionIdsContains(c.getId());
    }

    private long contarProductos(Categoria c) {
        Criteria cr = c.getTipo() == TipoCategoria.CATEGORIA
                ? Criteria.where("categoriaId").is(c.getId())
                : Criteria.where("coleccionIds").is(c.getId());
        return mongo.count(new Query(cr), "productos");
    }

    public CategoriaDto dto(Categoria c) {
        DescuentoDto d = null;
        if (c.getDescuento() != null) {
            d = new DescuentoDto(c.getDescuento().getPorcentaje(), c.getDescuento().getInicio(),
                    c.getDescuento().getFin(), c.getDescuento().vigente(precios.hoy()));
        }
        return new CategoriaDto(c.getId(), c.getNombre(), c.getTipo().name(), c.getTipo().getTexto(),
                c.getDescripcion(), d, contarProductos(c));
    }

    public static TipoCategoria parseTipo(String tipo) {
        try {
            return TipoCategoria.valueOf(tipo.trim().toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("Tipo no válido. Usa CATEGORIA, EDAD o TEMPORADA");
        }
    }
}
