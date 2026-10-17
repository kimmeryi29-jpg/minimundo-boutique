package com.minimundo.catalogo.service;

import com.minimundo.catalogo.dto.CatalogoDtos.ColeccionRef;
import com.minimundo.catalogo.dto.CatalogoDtos.DescuentoDto;
import com.minimundo.catalogo.dto.CatalogoDtos.DescuentoRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.PrecioSku;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoActivo;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoDto;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoResumen;
import com.minimundo.catalogo.dto.CatalogoDtos.VarianteDto;
import com.minimundo.catalogo.dto.CatalogoDtos.VarianteInfo;
import com.minimundo.catalogo.dto.CatalogoDtos.VarianteRequest;
import com.minimundo.catalogo.error.ApiException;
import com.minimundo.catalogo.model.Categoria;
import com.minimundo.catalogo.model.Descuento;
import com.minimundo.catalogo.model.EstadoProducto;
import com.minimundo.catalogo.model.Genero;
import com.minimundo.catalogo.model.ImagenRef;
import com.minimundo.catalogo.model.Producto;
import com.minimundo.catalogo.model.RangoEdad;
import com.minimundo.catalogo.model.TipoCategoria;
import com.minimundo.catalogo.model.Variante;
import com.minimundo.catalogo.repository.ProductoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Productos del catálogo: creación con variantes (HU-16, HU-17, HU-24, HU-25), imágenes (HU-18), edición (HU-19),
 * desactivación (HU-20), borrador y publicación (HU-22), descuentos (HU-23), catálogo público con filtros,
 * búsqueda y detalle (HU-26 a HU-29) y contratos internos para pedidos, logística y atención.
 */
@Service
public class ProductoService {

    public static final int MAX_IMAGENES = 8;

    private final ProductoRepository repo;
    private final MongoTemplate mongo;
    private final CategoriaService categorias;
    private final PrecioService precios;
    private final ImagenService imagenes;
    private final Clock clock;

    public ProductoService(ProductoRepository repo, MongoTemplate mongo, CategoriaService categorias,
                           PrecioService precios, ImagenService imagenes, Clock clock) {
        this.repo = repo;
        this.mongo = mongo;
        this.categorias = categorias;
        this.precios = precios;
        this.imagenes = imagenes;
        this.clock = clock;
    }

    public Producto obtener(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("El producto no existe"));
    }

    // ------------------------------------------------------------------ Catálogo público

    /** HU-27 y HU-28: solo productos publicados y activos que cumplen todos los filtros. */
    public List<ProductoResumen> buscarPublico(String texto, String categoria, String coleccion, String edad,
                                               String talla, String genero, Long precioMin, Long precioMax,
                                               String orden) {
        Query q = new Query(Criteria.where("estado").is(EstadoProducto.PUBLICADO).and("activo").is(true));
        if (texto != null && !texto.isBlank()) {
            q.addCriteria(Criteria.where("nombre").regex(Pattern.quote(texto.trim()), "i"));
        }
        if (categoria != null && !categoria.isBlank()) {
            q.addCriteria(Criteria.where("categoriaId").is(categoria));
        }
        if (coleccion != null && !coleccion.isBlank()) {
            q.addCriteria(Criteria.where("coleccionIds").is(coleccion));
        }
        if (edad != null && !edad.isBlank()) {
            q.addCriteria(Criteria.where("rangoEdad").is(parseEnum(RangoEdad.class, edad, "rango de edad")));
        }
        if (talla != null && !talla.isBlank()) {
            q.addCriteria(Criteria.where("variantes.talla").regex("^" + Pattern.quote(talla.trim()) + "$", "i"));
        }
        if (genero != null && !genero.isBlank()) {
            Genero g = parseEnum(Genero.class, genero, "género");
            q.addCriteria(g == Genero.UNISEX ? Criteria.where("genero").is(g)
                    : Criteria.where("genero").in(g, Genero.UNISEX));
        }
        if (precioMin != null || precioMax != null) {
            Criteria c = Criteria.where("precio");
            if (precioMin != null) {
                c = c.gte(precioMin);
            }
            if (precioMax != null) {
                c = c.lte(precioMax);
            }
            q.addCriteria(c);
        }
        if ("precio_asc".equals(orden)) {
            q.with(Sort.by(Sort.Direction.ASC, "precio"));
        } else if ("precio_desc".equals(orden)) {
            q.with(Sort.by(Sort.Direction.DESC, "precio"));
        } else if ("nombre".equals(orden)) {
            q.with(Sort.by(Sort.Direction.ASC, "nombre"));
        } else {
            q.with(Sort.by(Sort.Direction.DESC, "fechaPublicacion", "fechaCreacion"));
        }
        Map<String, Categoria> cats = categorias.mapa();
        return mongo.find(q, Producto.class).stream().map(p -> resumen(p, cats)).toList();
    }

    /** HU-29. HU-20: un producto desactivado o en borrador no se muestra a los clientes. */
    public ProductoDto detallePublico(String id, boolean gestion) {
        Producto p = repo.findById(id).orElseThrow(() -> ApiException.notFound("Producto no disponible"));
        if (!gestion && !p.visibleParaClientes()) {
            throw ApiException.notFound("Producto no disponible");
        }
        return dto(p, categorias.mapa());
    }

    // ------------------------------------------------------------------ Gestión del operario

    public List<ProductoResumen> listarGestion(String texto, String estado, Boolean activo) {
        Query q = new Query();
        if (texto != null && !texto.isBlank()) {
            String patron = Pattern.quote(texto.trim());
            q.addCriteria(new Criteria().orOperator(Criteria.where("nombre").regex(patron, "i"),
                    Criteria.where("codigo").regex(patron, "i"), Criteria.where("variantes.sku").regex(patron, "i")));
        }
        if (estado != null && !estado.isBlank()) {
            q.addCriteria(Criteria.where("estado").is(parseEnum(EstadoProducto.class, estado, "estado")));
        }
        if (activo != null) {
            q.addCriteria(Criteria.where("activo").is(activo));
        }
        q.with(Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        Map<String, Categoria> cats = categorias.mapa();
        return mongo.find(q, Producto.class).stream().map(p -> resumen(p, cats)).toList();
    }

    /** HU-16, HU-17, HU-22, HU-24, HU-25. */
    public ProductoDto crear(ProductoRequest req) {
        validarReferencias(req);
        Producto p = new Producto();
        aplicarDatos(p, req);
        p.setCodigo(generarCodigo(req.nombre()));
        p.setVariantes(construirVariantes(p.getCodigo(), req.variantes(), new ArrayList<>()));
        p.setActivo(true);
        p.setFechaCreacion(Instant.now(clock));
        p.setEstado(EstadoProducto.BORRADOR);
        if (req.publicar()) {
            p.setEstado(EstadoProducto.PUBLICADO);
            p.setFechaPublicacion(Instant.now(clock));
        }
        return dto(guardar(p), categorias.mapa());
    }

    /** HU-19: los pedidos ya creados conservan su precio porque guardan una copia (snapshot). */
    public ProductoDto actualizar(String id, ProductoRequest req) {
        Producto p = obtener(id);
        validarReferencias(req);
        aplicarDatos(p, req);
        p.setVariantes(construirVariantes(p.getCodigo(), req.variantes(), p.getVariantes()));
        p.setFechaActualizacion(Instant.now(clock));
        if (req.publicar() && p.getEstado() == EstadoProducto.BORRADOR) {
            validarPublicable(p);
            p.setEstado(EstadoProducto.PUBLICADO);
            p.setFechaPublicacion(Instant.now(clock));
        }
        return dto(guardar(p), categorias.mapa());
    }

    /** HU-22: solo se publica un borrador completo; si falta algo se informa el detalle. */
    public ProductoDto publicar(String id) {
        Producto p = obtener(id);
        validarPublicable(p);
        p.setEstado(EstadoProducto.PUBLICADO);
        if (p.getFechaPublicacion() == null) {
            p.setFechaPublicacion(Instant.now(clock));
        }
        p.setFechaActualizacion(Instant.now(clock));
        return dto(repo.save(p), categorias.mapa());
    }

    public ProductoDto volverABorrador(String id) {
        Producto p = obtener(id);
        p.setEstado(EstadoProducto.BORRADOR);
        p.setFechaActualizacion(Instant.now(clock));
        return dto(repo.save(p), categorias.mapa());
    }

    /** HU-20: el producto deja de mostrarse pero conserva su historial (no se borra). */
    public ProductoDto cambiarActivo(String id, boolean activo) {
        Producto p = obtener(id);
        p.setActivo(activo);
        p.setFechaActualizacion(Instant.now(clock));
        return dto(repo.save(p), categorias.mapa());
    }

    /** HU-17: agrega una variante; el SKU se genera con código-talla-color (p. ej. BOD-001-6M-ROSA). */
    public ProductoDto agregarVariante(String id, VarianteRequest req) {
        Producto p = obtener(id);
        List<VarianteRequest> todas = new ArrayList<>();
        p.getVariantes().forEach(v -> todas.add(new VarianteRequest(v.getTalla(), v.getColor())));
        todas.add(req);
        p.setVariantes(construirVariantes(p.getCodigo(), todas, p.getVariantes()));
        p.setFechaActualizacion(Instant.now(clock));
        return dto(guardar(p), categorias.mapa());
    }

    public ProductoDto eliminarVariante(String id, String sku) {
        Producto p = obtener(id);
        if (p.getVariantes().size() <= 1) {
            throw ApiException.badRequest("El producto debe conservar al menos una variante");
        }
        boolean quitada = p.getVariantes().removeIf(v -> v.getSku().equalsIgnoreCase(sku));
        if (!quitada) {
            throw ApiException.notFound("La variante " + sku + " no existe en este producto");
        }
        p.setFechaActualizacion(Instant.now(clock));
        return dto(repo.save(p), categorias.mapa());
    }

    /** HU-18: varias imágenes JPG o PNG por producto. */
    public ProductoDto agregarImagenes(String id, List<MultipartFile> archivos) {
        Producto p = obtener(id);
        if (archivos == null || archivos.isEmpty() || archivos.stream().allMatch(MultipartFile::isEmpty)) {
            throw ApiException.badRequest("Selecciona al menos una imagen");
        }
        List<MultipartFile> validos = archivos.stream().filter(a -> !a.isEmpty()).toList();
        if (p.getImagenes().size() + validos.size() > MAX_IMAGENES) {
            throw ApiException.badRequest("Un producto puede tener máximo " + MAX_IMAGENES + " imágenes");
        }
        validos.forEach(imagenes::validar);
        for (MultipartFile a : validos) {
            String imgId = imagenes.guardar(a, p.getId());
            p.getImagenes().add(new ImagenRef(imgId, a.getOriginalFilename(),
                    ImagenService.tipoDe(a.getContentType(), a.getOriginalFilename())));
        }
        p.setFechaActualizacion(Instant.now(clock));
        return dto(repo.save(p), categorias.mapa());
    }

    public ProductoDto eliminarImagen(String id, String imagenId) {
        Producto p = obtener(id);
        boolean quitada = p.getImagenes().removeIf(i -> i.getId().equals(imagenId));
        if (!quitada) {
            throw ApiException.notFound("La imagen no pertenece a este producto");
        }
        imagenes.eliminar(imagenId);
        p.setFechaActualizacion(Instant.now(clock));
        return dto(repo.save(p), categorias.mapa());
    }

    /** HU-23. */
    public ProductoDto ponerDescuento(String id, DescuentoRequest req) {
        CategoriaService.validarDescuento(req);
        Producto p = obtener(id);
        p.setDescuento(new Descuento(req.porcentaje(), req.inicio(), req.fin()));
        p.setFechaActualizacion(Instant.now(clock));
        return dto(repo.save(p), categorias.mapa());
    }

    public ProductoDto quitarDescuento(String id) {
        Producto p = obtener(id);
        p.setDescuento(null);
        p.setFechaActualizacion(Instant.now(clock));
        return dto(repo.save(p), categorias.mapa());
    }

    // ------------------------------------------------------------------ Contratos internos

    /** Precios vigentes por SKU para el checkout de ms-pedidos (sección 5.6). */
    public List<PrecioSku> precios(List<String> skus) {
        Set<String> buscados = new HashSet<>();
        skus.forEach(s -> buscados.add(s.toUpperCase(Locale.ROOT)));
        Map<String, Categoria> cats = categorias.mapa();
        List<PrecioSku> res = new ArrayList<>();
        for (Producto p : repo.findByVariantesSkuIn(new ArrayList<>(buscados))) {
            int pct = precios.descuentoVigente(p, cats);
            long finalP = PrecioService.aplicar(p.getPrecio(), pct);
            String img = p.getImagenes().isEmpty() ? null : p.getImagenes().get(0).getId();
            for (Variante v : p.getVariantes()) {
                if (buscados.contains(v.getSku())) {
                    res.add(new PrecioSku(v.getSku(), p.getId(), p.getNombre(), v.getTalla(), v.getColor(),
                            p.getPrecio(), finalP, pct, p.visibleParaClientes(), img));
                }
            }
        }
        return res;
    }

    public List<VarianteInfo> todasLasVariantes() {
        List<VarianteInfo> res = new ArrayList<>();
        for (Producto p : repo.findAll(Sort.by("nombre"))) {
            for (Variante v : p.getVariantes()) {
                res.add(info(p, v));
            }
        }
        return res;
    }

    public VarianteInfo variantePorSku(String sku) {
        Producto p = repo.findByVariantesSku(sku.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("El SKU " + sku + " no existe en el catálogo"));
        return p.getVariantes().stream().filter(v -> v.getSku().equalsIgnoreCase(sku)).findFirst()
                .map(v -> info(p, v))
                .orElseThrow(() -> ApiException.notFound("El SKU " + sku + " no existe en el catálogo"));
    }

    /** Para el cambio de talla (HU-64): variante del mismo producto y color con la talla deseada. */
    public VarianteInfo varianteDe(String productoId, String talla, String color) {
        Producto p = obtener(productoId);
        return p.getVariantes().stream()
                .filter(v -> v.getTalla().equalsIgnoreCase(talla.trim()))
                .filter(v -> color == null || color.isBlank() || v.getColor().equalsIgnoreCase(color.trim()))
                .findFirst().map(v -> info(p, v))
                .orElseThrow(() -> ApiException.notFound("El producto no se fabrica en talla " + talla
                        + (color == null || color.isBlank() ? "" : " y color " + color)));
    }

    public List<ProductoActivo> activos() {
        return repo.findByEstadoAndActivoTrue(EstadoProducto.PUBLICADO).stream()
                .map(p -> new ProductoActivo(p.getId(), p.getCodigo(), p.getNombre(),
                        p.getFechaPublicacion() != null ? p.getFechaPublicacion() : p.getFechaCreacion()))
                .toList();
    }

    // ------------------------------------------------------------------ Apoyo

    private VarianteInfo info(Producto p, Variante v) {
        return new VarianteInfo(v.getSku(), p.getId(), p.getNombre(), p.getCodigo(), v.getTalla(), v.getColor(),
                p.isActivo(), p.getEstado() == EstadoProducto.PUBLICADO);
    }

    private Producto guardar(Producto p) {
        try {
            return repo.save(p);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Ya existe una variante con ese SKU en el catálogo");
        }
    }

    private void validarReferencias(ProductoRequest req) {
        Categoria cat = categorias.obtener(req.categoriaId());
        if (cat.getTipo() != TipoCategoria.CATEGORIA) {
            throw ApiException.badRequest("Selecciona una categoría válida",
                    Map.of("categoriaId", "Selecciona una categoría (no una colección)"));
        }
        if (req.coleccionIds() != null) {
            for (String cid : req.coleccionIds()) {
                if (cid == null || cid.isBlank()) {
                    continue;
                }
                Categoria col = categorias.obtener(cid);
                if (col.getTipo() == TipoCategoria.CATEGORIA) {
                    throw ApiException.badRequest("\"" + col.getNombre() + "\" no es una colección");
                }
            }
        }
    }

    private void aplicarDatos(Producto p, ProductoRequest req) {
        p.setNombre(req.nombre().trim());
        p.setDescripcion(req.descripcion() == null ? null : req.descripcion().trim());
        p.setCategoriaId(req.categoriaId());
        p.setColeccionIds(req.coleccionIds() == null ? new ArrayList<>()
                : req.coleccionIds().stream().filter(c -> c != null && !c.isBlank()).distinct().toList());
        p.setGenero(parseEnum(Genero.class, req.genero(), "género"));
        p.setRangoEdad(parseEnum(RangoEdad.class, req.rangoEdad(), "rango de edad"));
        p.setPrecio(req.precio());
    }

    /**
     * Construye las variantes conservando los SKU existentes. HU-25: un SKU repetido dentro del producto
     * o en otro producto del catálogo responde 409.
     */
    private List<Variante> construirVariantes(String codigo, List<VarianteRequest> pedidas, List<Variante> actuales) {
        Map<String, Variante> existentes = new LinkedHashMap<>();
        actuales.forEach(v -> existentes.put(v.getSku(), v));
        Map<String, Variante> resultado = new LinkedHashMap<>();
        for (VarianteRequest r : pedidas) {
            if (r.talla() == null || r.talla().isBlank()) {
                throw ApiException.badRequest("Cada variante necesita una talla", Map.of("variantes", "Falta la talla"));
            }
            if (r.color() == null || r.color().isBlank()) {
                throw ApiException.badRequest("Cada variante necesita un color", Map.of("variantes", "Falta el color"));
            }
            String talla = r.talla().trim().toUpperCase(Locale.ROOT);
            String color = Textos.capitalizar(r.color());
            String sku = codigo + "-" + Textos.paraSku(talla) + "-" + Textos.paraSku(color);
            if (resultado.containsKey(sku)) {
                throw ApiException.conflict("La variante talla " + talla + " color " + color + " está repetida");
            }
            Variante existente = existentes.get(sku);
            if (existente == null && repo.existsByVariantesSku(sku)) {
                throw ApiException.conflict("El SKU " + sku + " ya existe en el catálogo");
            }
            resultado.put(sku, existente != null ? existente : new Variante(sku, talla, color));
        }
        if (resultado.isEmpty()) {
            throw ApiException.badRequest("Agrega al menos una variante de talla y color",
                    Map.of("variantes", "Agrega al menos una variante"));
        }
        return new ArrayList<>(resultado.values());
    }

    private void validarPublicable(Producto p) {
        Map<String, String> faltantes = new LinkedHashMap<>();
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            faltantes.put("nombre", "Falta el nombre");
        }
        if (p.getPrecio() <= 0) {
            faltantes.put("precio", "Falta el precio");
        }
        if (p.getVariantes() == null || p.getVariantes().isEmpty()) {
            faltantes.put("variantes", "Falta al menos una variante");
        }
        if (!faltantes.isEmpty()) {
            throw ApiException.badRequest("No se puede publicar: " + String.join(", ", faltantes.values()).toLowerCase(),
                    faltantes);
        }
    }

    /** Código base del producto: tres letras del nombre y un consecutivo (p. ej. BOD-001). */
    private String generarCodigo(String nombre) {
        String letras = Textos.paraSku(nombre);
        String prefijo = (letras + "XXX").substring(0, 3);
        long n = repo.countByCodigoStartingWith(prefijo + "-") + 1;
        String codigo = String.format("%s-%03d", prefijo, n);
        while (repo.existsByCodigo(codigo)) {
            n++;
            codigo = String.format("%s-%03d", prefijo, n);
        }
        return codigo;
    }

    static <E extends Enum<E>> E parseEnum(Class<E> tipo, String valor, String campo) {
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Valor no válido para " + campo + ": " + valor);
        }
    }

    // ------------------------------------------------------------------ Conversión a DTO

    public ProductoDto dto(Producto p, Map<String, Categoria> cats) {
        int pct = precios.descuentoVigente(p, cats);
        Categoria cat = p.getCategoriaId() == null ? null : cats.get(p.getCategoriaId());
        List<ColeccionRef> cols = p.getColeccionIds().stream().map(cats::get).filter(c -> c != null)
                .map(c -> new ColeccionRef(c.getId(), c.getNombre(), c.getTipo().name())).toList();
        DescuentoDto d = null;
        if (p.getDescuento() != null) {
            d = new DescuentoDto(p.getDescuento().getPorcentaje(), p.getDescuento().getInicio(),
                    p.getDescuento().getFin(), p.getDescuento().vigente(precios.hoy()));
        }
        return new ProductoDto(p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(), p.getCategoriaId(),
                cat == null ? null : cat.getNombre(), cols,
                p.getGenero() == null ? null : p.getGenero().name(),
                p.getGenero() == null ? null : p.getGenero().getTexto(),
                p.getRangoEdad() == null ? null : p.getRangoEdad().name(),
                p.getRangoEdad() == null ? null : p.getRangoEdad().getTexto(),
                p.getPrecio(), PrecioService.aplicar(p.getPrecio(), pct), pct, d,
                p.getEstado().name(), p.isActivo(), p.getImagenes().stream().map(ImagenRef::getId).toList(),
                p.getVariantes().stream().map(v -> new VarianteDto(v.getSku(), v.getTalla(), v.getColor())).toList(),
                tallas(p), colores(p), p.getFechaCreacion(), p.getFechaActualizacion());
    }

    private ProductoResumen resumen(Producto p, Map<String, Categoria> cats) {
        int pct = precios.descuentoVigente(p, cats);
        Categoria cat = p.getCategoriaId() == null ? null : cats.get(p.getCategoriaId());
        return new ProductoResumen(p.getId(), p.getCodigo(), p.getNombre(), cat == null ? null : cat.getNombre(),
                p.getGenero() == null ? null : p.getGenero().name(),
                p.getGenero() == null ? null : p.getGenero().getTexto(),
                p.getRangoEdad() == null ? null : p.getRangoEdad().name(),
                p.getRangoEdad() == null ? null : p.getRangoEdad().getTexto(),
                p.getPrecio(), PrecioService.aplicar(p.getPrecio(), pct), pct,
                p.getImagenes().isEmpty() ? null : p.getImagenes().get(0).getId(), tallas(p), colores(p),
                p.getEstado().name(), p.isActivo(), p.getVariantes().size());
    }

    private static List<String> tallas(Producto p) {
        Set<String> s = new LinkedHashSet<>();
        p.getVariantes().forEach(v -> s.add(v.getTalla()));
        return new ArrayList<>(s);
    }

    private static List<String> colores(Producto p) {
        Set<String> s = new LinkedHashSet<>();
        p.getVariantes().forEach(v -> s.add(v.getColor()));
        return new ArrayList<>(s);
    }
}
