package com.minimundo.catalogo.config;

import com.minimundo.catalogo.model.Categoria;
import com.minimundo.catalogo.model.Descuento;
import com.minimundo.catalogo.model.EstadoProducto;
import com.minimundo.catalogo.model.Genero;
import com.minimundo.catalogo.model.ImagenRef;
import com.minimundo.catalogo.model.Producto;
import com.minimundo.catalogo.model.RangoEdad;
import com.minimundo.catalogo.model.TipoCategoria;
import com.minimundo.catalogo.model.Variante;
import com.minimundo.catalogo.repository.CategoriaRepository;
import com.minimundo.catalogo.repository.ProductoRepository;
import com.minimundo.catalogo.service.ImagenService;
import com.minimundo.catalogo.service.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de demostración (DEMO_DATA=true): categorías, colecciones y productos con variantes e imágenes.
 * Los SKU coinciden con el inventario inicial que carga ms-logistica.
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final ImagenService imagenes;
    private final boolean demo;
    private final Map<String, String> ids = new HashMap<>();
    private final LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

    public DataSeeder(ProductoRepository productos, CategoriaRepository categorias, ImagenService imagenes,
                      @Value("${minimundo.demo-data:false}") boolean demo) {
        this.productos = productos;
        this.categorias = categorias;
        this.imagenes = imagenes;
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!demo || productos.count() > 0) {
            return;
        }
        categoria("Bodies", TipoCategoria.CATEGORIA);
        categoria("Vestidos", TipoCategoria.CATEGORIA);
        categoria("Conjuntos", TipoCategoria.CATEGORIA);
        categoria("Pijamas", TipoCategoria.CATEGORIA);
        categoria("Camisetas", TipoCategoria.CATEGORIA);
        categoria("Pantalones", TipoCategoria.CATEGORIA);
        categoria("Chaquetas", TipoCategoria.CATEGORIA);
        categoria("Calzado", TipoCategoria.CATEGORIA);
        categoria("Accesorios", TipoCategoria.CATEGORIA);
        categoria("Recién nacido", TipoCategoria.EDAD);
        categoria("Primeros pasos", TipoCategoria.EDAD);
        categoria("Escolares", TipoCategoria.EDAD);
        categoria("Día del Niño", TipoCategoria.TEMPORADA);
        categoria("Navidad", TipoCategoria.TEMPORADA);
        categoria("Vacaciones", TipoCategoria.TEMPORADA);
        Categoria calzado = categorias.findById(ids.get("Calzado")).orElseThrow();
        calzado.setDescuento(new Descuento(10, hoy.minusDays(2), hoy.plusDays(60)));
        categorias.save(calzado);
        producto("BOD-001", "Body manga larga de algodón",
                "Body 100 % algodón orgánico con broches en la entrepierna para cambiar el pañal fácilmente. Suave con la piel del bebé.",
                "Bodies", List.of("Recién nacido"), Genero.UNISEX, RangoEdad.M0_12, 34900L,
                List.of(new Variante("BOD-001-03M-ROSA", "0-3M", "Rosa"), new Variante("BOD-001-03M-BLANCO", "0-3M", "Blanco"), new Variante("BOD-001-03M-CELESTE", "0-3M", "Celeste"), new Variante("BOD-001-36M-ROSA", "3-6M", "Rosa"), new Variante("BOD-001-36M-BLANCO", "3-6M", "Blanco"), new Variante("BOD-001-36M-CELESTE", "3-6M", "Celeste"), new Variante("BOD-001-69M-ROSA", "6-9M", "Rosa"), new Variante("BOD-001-69M-BLANCO", "6-9M", "Blanco"), new Variante("BOD-001-69M-CELESTE", "6-9M", "Celeste")),
                List.of("bod-001-1", "bod-001-2"), EstadoProducto.PUBLICADO, 0);
        producto("VES-001", "Vestido de tul con flores",
                "Vestido de fiesta con falda de tul y flores bordadas. Forro interior de algodón y cierre posterior.",
                "Vestidos", List.of("Día del Niño"), Genero.NINA, RangoEdad.A1_3, 89900L,
                List.of(new Variante("VES-001-2-ROSA", "2", "Rosa"), new Variante("VES-001-2-LILA", "2", "Lila"), new Variante("VES-001-4-ROSA", "4", "Rosa"), new Variante("VES-001-4-LILA", "4", "Lila")),
                List.of("ves-001-1", "ves-001-2"), EstadoProducto.PUBLICADO, 15);
        producto("CON-001", "Conjunto deportivo de algodón",
                "Buzo con cremallera y pantalón jogger en algodón perchado. Ideal para el colegio y el parque.",
                "Conjuntos", List.of("Escolares"), Genero.NINO, RangoEdad.A4_6, 79900L,
                List.of(new Variante("CON-001-4-AZUL", "4", "Azul"), new Variante("CON-001-4-GRIS", "4", "Gris"), new Variante("CON-001-6-AZUL", "6", "Azul"), new Variante("CON-001-6-GRIS", "6", "Gris")),
                List.of("con-001-1", "con-001-2"), EstadoProducto.PUBLICADO, 0);
        producto("PIJ-001", "Pijama de dinosaurios",
                "Pijama de dos piezas en algodón suave con estampado de dinosaurios que brillan en la oscuridad.",
                "Pijamas", List.of(), Genero.NINO, RangoEdad.A4_6, 59900L,
                List.of(new Variante("PIJ-001-4-VERDE", "4", "Verde"), new Variante("PIJ-001-6-VERDE", "6", "Verde"), new Variante("PIJ-001-8-VERDE", "8", "Verde")),
                List.of("pij-001-1"), EstadoProducto.PUBLICADO, 0);
        producto("CAM-001", "Camiseta estampada arcoíris",
                "Camiseta de algodón peinado con estampado de arcoíris. Cuello redondo y costuras reforzadas.",
                "Camisetas", List.of("Vacaciones"), Genero.UNISEX, RangoEdad.A7_9, 32900L,
                List.of(new Variante("CAM-001-8-BLANCO", "8", "Blanco"), new Variante("CAM-001-8-AMARILLO", "8", "Amarillo"), new Variante("CAM-001-10-BLANCO", "10", "Blanco"), new Variante("CAM-001-10-AMARILLO", "10", "Amarillo")),
                List.of("cam-001-1"), EstadoProducto.PUBLICADO, 0);
        producto("PAN-001", "Jean clásico ajustable",
                "Jean en denim elástico con pretina ajustable por dentro para que crezca con tu hijo.",
                "Pantalones", List.of("Escolares"), Genero.NINA, RangoEdad.A7_9, 69900L,
                List.of(new Variante("PAN-001-8-AZUL", "8", "Azul"), new Variante("PAN-001-10-AZUL", "10", "Azul"), new Variante("PAN-001-12-AZUL", "12", "Azul")),
                List.of("pan-001-1"), EstadoProducto.PUBLICADO, 0);
        producto("CHA-001", "Chaqueta impermeable con capota",
                "Chaqueta liviana impermeable con capota desmontable y bolsillos con cierre. Perfecta para la lluvia.",
                "Chaquetas", List.of("Vacaciones"), Genero.UNISEX, RangoEdad.A10_12, 119900L,
                List.of(new Variante("CHA-001-10-ROJO", "10", "Rojo"), new Variante("CHA-001-10-AZUL", "10", "Azul"), new Variante("CHA-001-12-ROJO", "12", "Rojo"), new Variante("CHA-001-12-AZUL", "12", "Azul")),
                List.of("cha-001-1", "cha-001-2"), EstadoProducto.PUBLICADO, 0);
        producto("TEN-001", "Tenis con luces",
                "Tenis con suela que se ilumina al caminar, cierre de velcro y plantilla acolchada.",
                "Calzado", List.of("Día del Niño"), Genero.UNISEX, RangoEdad.A4_6, 99900L,
                List.of(new Variante("TEN-001-26-BLANCO", "26", "Blanco"), new Variante("TEN-001-26-ROSA", "26", "Rosa"), new Variante("TEN-001-28-BLANCO", "28", "Blanco"), new Variante("TEN-001-28-ROSA", "28", "Rosa"), new Variante("TEN-001-30-BLANCO", "30", "Blanco"), new Variante("TEN-001-30-ROSA", "30", "Rosa")),
                List.of("zap-001-1", "zap-001-2"), EstadoProducto.PUBLICADO, 0);
        producto("GOR-001", "Gorro tejido con pompón",
                "Gorro tejido en lana suave con pompón. Abriga sin causar picazón.",
                "Accesorios", List.of("Navidad", "Recién nacido"), Genero.UNISEX, RangoEdad.M0_12, 24900L,
                List.of(new Variante("GOR-001-UNICA-BEIGE", "UNICA", "Beige"), new Variante("GOR-001-UNICA-ROSA", "UNICA", "Rosa")),
                List.of("gor-001-1", "gor-001-2"), EstadoProducto.PUBLICADO, 0);
        producto("BOD-002", "Body manga corta estampado",
                "Body de manga corta con estampado de corazones. Algodón fresco para clima cálido.",
                "Bodies", List.of("Recién nacido"), Genero.NINA, RangoEdad.M0_12, 29900L,
                List.of(new Variante("BOD-002-36M-LILA", "3-6M", "Lila"), new Variante("BOD-002-69M-LILA", "6-9M", "Lila"), new Variante("BOD-002-912M-LILA", "9-12M", "Lila")),
                List.of("bod-002-1"), EstadoProducto.PUBLICADO, 0);
        producto("VES-002", "Vestido navideño de terciopelo",
                "Vestido de terciopelo rojo con moño en la cintura, ideal para las celebraciones de diciembre.",
                "Vestidos", List.of("Navidad"), Genero.NINA, RangoEdad.A4_6, 109900L,
                List.of(new Variante("VES-002-4-ROJO", "4", "Rojo"), new Variante("VES-002-6-ROJO", "6", "Rojo")),
                List.of("ves-002-1"), EstadoProducto.PUBLICADO, 0);
        producto("PIJ-002", "Pijama enteriza osito",
                "Pijama enteriza con capucha de osito y pies antideslizantes. (Borrador en revisión).",
                "Pijamas", List.of(), Genero.UNISEX, RangoEdad.M0_12, 64900L,
                List.of(new Variante("PIJ-002-69M-AMARILLO", "6-9M", "Amarillo"), new Variante("PIJ-002-912M-AMARILLO", "9-12M", "Amarillo")),
                List.of("pij-002-1"), EstadoProducto.BORRADOR, 0);
        log.info("Catálogo de demostración cargado: {} productos", productos.count());
    }

    private void categoria(String nombre, TipoCategoria tipo) {
        Categoria c = new Categoria();
        c.setNombre(nombre);
        c.setNombreNormalizado(Textos.normalizar(nombre));
        c.setTipo(tipo);
        c.setFechaCreacion(Instant.now());
        ids.put(nombre, categorias.save(c).getId());
    }

    private void producto(String codigo, String nombre, String descripcion, String categoria, List<String> colecciones,
                          Genero genero, RangoEdad edad, long precio, List<Variante> variantes, List<String> fotos,
                          EstadoProducto estado, int descuento) {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setCategoriaId(ids.get(categoria));
        p.setColeccionIds(colecciones.stream().map(ids::get).toList());
        p.setGenero(genero);
        p.setRangoEdad(edad);
        p.setPrecio(precio);
        p.setVariantes(new ArrayList<>(variantes));
        p.setEstado(estado);
        p.setActivo(true);
        p.setFechaCreacion(Instant.now());
        if (estado == EstadoProducto.PUBLICADO) {
            p.setFechaPublicacion(Instant.now());
        }
        if (descuento > 0) {
            p.setDescuento(new Descuento(descuento, hoy.minusDays(3), hoy.plusDays(45)));
        }
        p = productos.save(p);
        for (String foto : fotos) {
            try (InputStream in = new ClassPathResource("demo/" + foto + ".png").getInputStream()) {
                String id = imagenes.guardar(in.readAllBytes(), foto + ".png", "image/png", p.getId());
                p.getImagenes().add(new ImagenRef(id, foto + ".png", "image/png"));
            } catch (IOException e) {
                log.warn("No se pudo cargar la imagen de demostración {}", foto);
            }
        }
        productos.save(p);
    }
}
