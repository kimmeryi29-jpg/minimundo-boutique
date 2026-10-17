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
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo ampliado con fotos reales (DEMO_DATA=true). Las fotos son de Pexels (https://www.pexels.com/license/):
 * uso gratuito, también comercial, sin atribución obligatoria. Se descargan al arrancar y se guardan en GridFS.
 * <ul>
 *   <li>Reemplaza los dibujos de los productos de demostración originales por fotos.</li>
 *   <li>Agrega prendas nuevas si todavía no existen (se identifican por su código).</li>
 * </ul>
 * Corre en segundo plano para no demorar el arranque; si una foto no descarga, el producto queda sin ella.
 * Los códigos de las prendas nuevas deben coincidir con {@code InventarioDemo} de ms-logistica.
 */
@Component
@Order(2)
public class CatalogoFotos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogoFotos.class);
    private static final String PREFIJO = "pexels-";

    record Prenda(String codigo, String nombre, String descripcion, String categoria, List<String> colecciones,
                  Genero genero, RangoEdad edad, long precio, List<String> tallas, List<String> colores,
                  List<Long> fotos, int descuento) {
    }

    /** Fotos para los productos de demostración originales. */
    static final Map<String, List<Long>> REEMPLAZOS = Map.ofEntries(
            Map.entry("BOD-001", List.of(29015875L)),
            Map.entry("VES-001", List.of(27174639L, 13624503L)),
            Map.entry("CON-001", List.of(8435762L)),
            Map.entry("PIJ-001", List.of(8501428L)),
            Map.entry("CAM-001", List.of(33980569L)),
            Map.entry("PAN-001", List.of(5560013L, 10133278L)),
            Map.entry("CHA-001", List.of(17098845L)),
            Map.entry("TEN-001", List.of(17895637L)),
            Map.entry("GOR-001", List.of(32890761L, 14788989L)),
            Map.entry("BOD-002", List.of(9467889L)),
            Map.entry("VES-002", List.of(29256997L, 29256999L, 36656594L)),
            Map.entry("PIJ-002", List.of(21643015L)));

    static final List<Prenda> NUEVAS = List.of(
            p("VES-003", "Vestido de rayas sin mangas", "Vestido fresco de algodón a rayas azules, con botones en la espalda. Para los días de calor.",
                    "Vestidos", List.of("Vacaciones"), Genero.NINA, RangoEdad.A1_3, 74900, List.of("1", "2", "3"), List.of("Azul"), List.of(38247111L), 0),
            p("VES-004", "Vestido floral negro", "Vestido de manga corta con estampado de flores pequeñas y falda con vuelo.",
                    "Vestidos", List.of(), Genero.NINA, RangoEdad.A4_6, 84900, List.of("4", "6"), List.of("Negro"), List.of(36691792L), 0),
            p("VES-005", "Vestido de fiesta con volantes", "Vestido rojo de capas con volantes, ideal para cumpleaños y celebraciones.",
                    "Vestidos", List.of("Navidad"), Genero.NINA, RangoEdad.A4_6, 99900, List.of("4", "6"), List.of("Rojo"), List.of(14831753L), 0),
            p("CON-002", "Conjunto camisa y bermuda", "Camisa estampada de manga corta y bermuda en dril verde oliva.",
                    "Conjuntos", List.of("Vacaciones"), Genero.NINO, RangoEdad.A1_3, 69900, List.of("2", "3"), List.of("Verde"), List.of(5693888L), 0),
            p("CON-003", "Conjunto marinero de rayas", "Camiseta y short a rayas con gorra a juego. Algodón suave para el verano.",
                    "Conjuntos", List.of("Vacaciones"), Genero.UNISEX, RangoEdad.M0_12, 79900, List.of("6-9M", "9-12M"), List.of("Negro"), List.of(34121887L), 0),
            p("CON-004", "Conjunto de lino para playa", "Camisa y short de lino con sombrero de ala corta. Liviano y transpirable.",
                    "Conjuntos", List.of("Vacaciones"), Genero.NINO, RangoEdad.A1_3, 89900, List.of("1", "2", "3"), List.of("Gris"), List.of(32848541L), 10),
            p("CON-005", "Conjunto de jean", "Chaqueta y pantalón en denim claro con camiseta verde.",
                    "Conjuntos", List.of(), Genero.NINO, RangoEdad.A4_6, 119900, List.of("4", "6"), List.of("Azul"), List.of(16396428L), 0),
            p("CON-006", "Set de recién nacido rosa", "Set de cinco piezas: body, pantalón, gorro, babero y patines tejidos.",
                    "Conjuntos", List.of("Recién nacido"), Genero.NINA, RangoEdad.M0_12, 99900, List.of("0-3M", "3-6M"), List.of("Rosa"), List.of(4858394L), 0),
            p("CON-007", "Conjunto tejido pastel", "Saco y gorro tejidos en lana suave de colores pastel.",
                    "Conjuntos", List.of("Recién nacido"), Genero.UNISEX, RangoEdad.M0_12, 89900, List.of("0-3M", "3-6M", "6-9M"), List.of("Azul"), List.of(36848838L), 0),
            p("CON-008", "Conjunto marinero azul", "Camiseta de rayas con cuello marinero y short azul.",
                    "Conjuntos", List.of("Vacaciones"), Genero.NINO, RangoEdad.A1_3, 79900, List.of("2", "3"), List.of("Azul"), List.of(5665128L), 0),
            p("CON-009", "Enterizo de rayas con sombrero", "Enterizo de rayas finas con sombrero de ala para el sol.",
                    "Conjuntos", List.of("Vacaciones"), Genero.UNISEX, RangoEdad.M0_12, 84900, List.of("6-9M", "9-12M"), List.of("Blanco"), List.of(8207810L), 0),
            p("CON-010", "Esqueleto de rayas y suéter mostaza", "Esqueleto de rayas rojas y suéter tejido mostaza para combinar.",
                    "Conjuntos", List.of(), Genero.UNISEX, RangoEdad.A1_3, 94900, List.of("1", "2"), List.of("Mostaza"), List.of(4964280L), 0),
            p("PIJ-003", "Pijama gris estampada", "Pijama de dos piezas en algodón perchado con estampado de animales.",
                    "Pijamas", List.of(), Genero.UNISEX, RangoEdad.A1_3, 54900, List.of("2", "3", "4"), List.of("Gris"), List.of(18820123L, 18820092L), 0),
            p("PIJ-004", "Pijama de cuadros", "Pijama de franela a cuadros con botones y pantalón largo.",
                    "Pijamas", List.of(), Genero.NINO, RangoEdad.A10_12, 69900, List.of("10", "12"), List.of("Azul"), List.of(12932248L), 0),
            p("PIJ-005", "Pijama navideña de cuadros", "Pijama roja de cuadros para la noche de Navidad. Tela suave que no da calor.",
                    "Pijamas", List.of("Navidad"), Genero.UNISEX, RangoEdad.A4_6, 64900, List.of("4", "6", "8"), List.of("Rojo"), List.of(37609136L), 0),
            p("PIJ-006", "Pijama enteriza estampada", "Pijama enteriza de algodón blanco con estampado de animales del bosque.",
                    "Pijamas", List.of(), Genero.NINA, RangoEdad.A1_3, 49900, List.of("2", "3"), List.of("Blanco"), List.of(18863554L), 0),
            p("CAM-002", "Camiseta básica gris", "Camiseta de algodón peinado con corte holgado. Combina con todo.",
                    "Camisetas", List.of("Escolares"), Genero.UNISEX, RangoEdad.A7_9, 29900, List.of("8", "10"), List.of("Gris"), List.of(30683087L), 0),
            p("CAM-003", "Camiseta básica negra", "Camiseta de algodón de cuello redondo, resistente a los lavados.",
                    "Camisetas", List.of("Escolares"), Genero.UNISEX, RangoEdad.A7_9, 29900, List.of("6", "8", "10"), List.of("Negro"), List.of(32071161L), 0),
            p("CAM-004", "Camiseta estampada de cometas", "Camiseta gris claro con estampado gráfico al frente.",
                    "Camisetas", List.of(), Genero.NINO, RangoEdad.A10_12, 34900, List.of("10", "12"), List.of("Gris"), List.of(29215113L), 0),
            p("CAM-005", "Camiseta roja estampada", "Camiseta roja de algodón con estampado divertido en el pecho.",
                    "Camisetas", List.of("Día del Niño"), Genero.NINO, RangoEdad.A4_6, 39900, List.of("4", "6"), List.of("Rojo"), List.of(33509295L), 0),
            p("BLU-001", "Blusa con bordado calado", "Blusa blanca de algodón con cuello y mangas de bordado calado.",
                    "Camisetas", List.of(), Genero.NINA, RangoEdad.A4_6, 59900, List.of("4", "6"), List.of("Blanco"), List.of(32234111L), 0),
            p("PAN-002", "Pantalón jogger de algodón", "Pantalón jogger con pretina elástica, suave y cómodo para jugar.",
                    "Pantalones", List.of("Escolares"), Genero.UNISEX, RangoEdad.A1_3, 39900, List.of("2", "3", "4"), List.of("Celeste", "Azul oscuro", "Blanco"), List.of(32233627L), 0),
            p("CHA-002", "Chaqueta de jean", "Chaqueta en denim con botones metálicos y bolsillos al pecho.",
                    "Chaquetas", List.of(), Genero.NINO, RangoEdad.A7_9, 109900, List.of("8", "10"), List.of("Azul"), List.of(38778561L), 0),
            p("CHA-003", "Chaqueta acolchada con capota", "Chaqueta inflada, liviana y abrigada, con capota y cierre frontal.",
                    "Chaquetas", List.of(), Genero.UNISEX, RangoEdad.A1_3, 129900, List.of("1", "2", "3"), List.of("Azul"), List.of(4260379L), 15),
            p("CHA-004", "Chaqueta negra de cuero sintético", "Chaqueta corta de cuero sintético con cremallera lateral.",
                    "Chaquetas", List.of(), Genero.NINA, RangoEdad.A4_6, 119900, List.of("4", "6"), List.of("Negro"), List.of(28808534L), 0),
            p("CHA-005", "Abrigo de peluche", "Abrigo de peluche suave con forro interior. Muy abrigado para el frío.",
                    "Chaquetas", List.of("Navidad"), Genero.NINA, RangoEdad.A1_3, 109900, List.of("2", "3"), List.of("Blanco"), List.of(28907988L), 0),
            p("SAC-001", "Saco de vestir azul marino", "Saco formal de corte entallado para matrimonios y ocasiones especiales.",
                    "Chaquetas", List.of(), Genero.NINO, RangoEdad.A4_6, 139900, List.of("4", "6"), List.of("Azul"), List.of(30690920L), 0),
            p("SUE-001", "Suéter tejido a rayas", "Suéter de punto con cuello de botones y rayas color crema.",
                    "Suéteres", List.of(), Genero.UNISEX, RangoEdad.A1_3, 79900, List.of("1", "2", "3"), List.of("Café"), List.of(28259753L), 0),
            p("SUE-002", "Suéter de estrellas", "Suéter mostaza de algodón con estrellas estampadas.",
                    "Suéteres", List.of(), Genero.UNISEX, RangoEdad.M0_12, 69900, List.of("6-9M", "9-12M"), List.of("Mostaza"), List.of(5427302L), 0),
            p("SUE-003", "Suéter tejido rosa", "Suéter tejido a mano con puntos en relieve, abrochado en la espalda.",
                    "Suéteres", List.of("Recién nacido"), Genero.NINA, RangoEdad.M0_12, 74900, List.of("3-6M", "6-9M"), List.of("Rosa"), List.of(30791355L), 0),
            p("ENT-001", "Enterizo de lino con botones", "Enterizo de lino lavado con cuello camisero y botones frontales.",
                    "Bodies", List.of(), Genero.UNISEX, RangoEdad.M0_12, 84900, List.of("6-9M", "9-12M"), List.of("Beige"), List.of(28259755L), 0),
            p("BAL-001", "Baletas de bebé con moño", "Baletas de charol con moño y suela blanda para los primeros pasos.",
                    "Calzado", List.of("Primeros pasos"), Genero.NINA, RangoEdad.M0_12, 49900, List.of("16", "17", "18"), List.of("Blanco"), List.of(37621032L), 0),
            p("ZAP-001", "Zapatos de conejito", "Zapatos de cuero suave con carita de conejo y suela antideslizante.",
                    "Calzado", List.of("Primeros pasos"), Genero.UNISEX, RangoEdad.M0_12, 54900, List.of("17", "18", "19"), List.of("Amarillo"), List.of(35753260L), 0),
            p("ZAP-002", "Zapatos con velcro turquesa", "Zapatos flexibles con cierre de velcro y puntera reforzada.",
                    "Calzado", List.of("Primeros pasos"), Genero.UNISEX, RangoEdad.M0_12, 59900, List.of("18", "19", "20"), List.of("Turquesa"), List.of(15844906L), 0),
            p("BOT-001", "Botas de cordón", "Botas de cuero con cordones y suela de caucho para todo terreno.",
                    "Calzado", List.of(), Genero.UNISEX, RangoEdad.A1_3, 89900, List.of("20", "22", "24"), List.of("Café"), List.of(13822967L), 0),
            p("TEN-002", "Tenis blancos de lona", "Tenis de lona con cordones y suela de caucho. Fáciles de lavar.",
                    "Calzado", List.of("Escolares"), Genero.UNISEX, RangoEdad.A1_3, 69900, List.of("22", "24", "26"), List.of("Blanco"), List.of(30910170L), 0),
            p("GOR-002", "Gorro tejido de zorrito", "Gorro tejido con orejas de zorro y saco a juego. Abriga sin picar.",
                    "Accesorios", List.of("Navidad", "Recién nacido"), Genero.UNISEX, RangoEdad.M0_12, 39900, List.of("UNICA"), List.of("Rojo"), List.of(6635185L), 0)
    );

    private static Prenda p(String codigo, String nombre, String descripcion, String categoria, List<String> colecciones,
                            Genero genero, RangoEdad edad, long precio, List<String> tallas, List<String> colores,
                            List<Long> fotos, int descuento) {
        return new Prenda(codigo, nombre, descripcion, categoria, colecciones, genero, edad, precio, tallas, colores,
                fotos, descuento);
    }

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final ImagenService imagenes;
    private final boolean demo;
    private final boolean activo;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public CatalogoFotos(ProductoRepository productos, CategoriaRepository categorias, ImagenService imagenes,
                         @Value("${minimundo.demo-data:false}") boolean demo,
                         @Value("${minimundo.fotos-demo:true}") boolean activo) {
        this.productos = productos;
        this.categorias = categorias;
        this.imagenes = imagenes;
        this.demo = demo;
        this.activo = activo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!demo || !activo) {
            return;
        }
        Thread.ofVirtual().name("catalogo-fotos").start(() -> {
            try {
                cargar();
            } catch (RuntimeException e) {
                log.warn("No se completó el catálogo con fotos: {}", e.getMessage());
            }
        });
    }

    void cargar() {
        Map<String, String> ids = new HashMap<>();
        categorias.findAll().forEach(c -> ids.put(c.getNombre(), c.getId()));
        if (!ids.containsKey("Suéteres")) {
            Categoria c = new Categoria();
            c.setNombre("Suéteres");
            c.setNombreNormalizado(Textos.normalizar("Suéteres"));
            c.setTipo(TipoCategoria.CATEGORIA);
            c.setFechaCreacion(Instant.now());
            ids.put("Suéteres", categorias.save(c).getId());
        }
        int reemplazados = 0;
        for (Producto p : productos.findAll()) {
            List<Long> fotos = REEMPLAZOS.get(p.getCodigo());
            boolean soloDibujos = p.getImagenes().stream().noneMatch(i -> i.getNombre() != null && i.getNombre().startsWith(PREFIJO));
            if (fotos == null || !soloDibujos) {
                continue;
            }
            List<ImagenRef> nuevas = descargar(fotos, p.getId());
            if (nuevas.isEmpty()) {
                continue;
            }
            p.getImagenes().stream().filter(i -> i.getNombre() != null && i.getNombre().endsWith(".png"))
                    .forEach(i -> imagenes.eliminar(i.getId()));
            p.setImagenes(new ArrayList<>(nuevas));
            productos.save(p);
            reemplazados++;
        }
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
        int creados = 0;
        for (Prenda n : NUEVAS) {
            if (productos.existsByCodigo(n.codigo()) || !ids.containsKey(n.categoria())) {
                continue;
            }
            Producto p = new Producto();
            p.setCodigo(n.codigo());
            p.setNombre(n.nombre());
            p.setDescripcion(n.descripcion());
            p.setCategoriaId(ids.get(n.categoria()));
            p.setColeccionIds(n.colecciones().stream().filter(ids::containsKey).map(ids::get).toList());
            p.setGenero(n.genero());
            p.setRangoEdad(n.edad());
            p.setPrecio(n.precio());
            List<Variante> variantes = new ArrayList<>();
            for (String talla : n.tallas()) {
                for (String color : n.colores()) {
                    variantes.add(new Variante(sku(n.codigo(), talla, color), talla, color));
                }
            }
            p.setVariantes(variantes);
            p.setEstado(EstadoProducto.PUBLICADO);
            p.setActivo(true);
            p.setFechaCreacion(Instant.now());
            p.setFechaPublicacion(Instant.now());
            if (n.descuento() > 0) {
                p.setDescuento(new Descuento(n.descuento(), hoy.minusDays(1), hoy.plusDays(45)));
            }
            p = productos.save(p);
            p.setImagenes(new ArrayList<>(descargar(n.fotos(), p.getId())));
            productos.save(p);
            creados++;
        }
        log.info("Catálogo con fotos: {} productos con fotos nuevas, {} prendas agregadas", reemplazados, creados);
    }

    static String sku(String codigo, String talla, String color) {
        return codigo + "-" + Textos.paraSku(talla) + "-" + Textos.paraSku(color);
    }

    private List<ImagenRef> descargar(List<Long> fotos, String productoId) {
        List<ImagenRef> refs = new ArrayList<>();
        for (Long foto : fotos) {
            String url = "https://images.pexels.com/photos/" + foto + "/pexels-photo-" + foto
                    + ".jpeg?auto=compress&cs=tinysrgb&w=900&h=900&fit=crop";
            try {
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "MiniMundoBoutique/1.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200 || r.body().length == 0 || r.body().length > imagenes.getMaxBytes()) {
                    log.warn("Foto {} no disponible (HTTP {})", foto, r.statusCode());
                    continue;
                }
                String nombre = PREFIJO + foto + ".jpg";
                String id = imagenes.guardar(r.body(), nombre, "image/jpeg", productoId);
                refs.add(new ImagenRef(id, nombre, "image/jpeg"));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("No se pudo descargar la foto {}: {}", foto, e.getMessage());
            }
        }
        return refs;
    }
}
