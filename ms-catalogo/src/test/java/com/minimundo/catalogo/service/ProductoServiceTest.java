package com.minimundo.catalogo.service;

import com.minimundo.catalogo.dto.CatalogoDtos.DescuentoRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.VarianteRequest;
import com.minimundo.catalogo.error.ApiException;
import com.minimundo.catalogo.model.Categoria;
import com.minimundo.catalogo.model.Descuento;
import com.minimundo.catalogo.model.EstadoProducto;
import com.minimundo.catalogo.model.Producto;
import com.minimundo.catalogo.model.TipoCategoria;
import com.minimundo.catalogo.model.Variante;
import com.minimundo.catalogo.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock ProductoRepository repo;
    @Mock MongoTemplate mongo;
    @Mock CategoriaService categorias;
    @Mock ImagenService imagenes;

    Clock clock = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
    PrecioService precios = new PrecioService(clock);
    ProductoService service;
    Categoria bodies;

    @BeforeEach
    void setUp() {
        service = new ProductoService(repo, mongo, categorias, precios, imagenes, clock);
        bodies = new Categoria();
        bodies.setId("c1");
        bodies.setNombre("Bodies");
        bodies.setTipo(TipoCategoria.CATEGORIA);
        lenient().when(categorias.obtener("c1")).thenReturn(bodies);
        lenient().when(categorias.mapa()).thenReturn(Map.of("c1", bodies));
        lenient().when(repo.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ProductoRequest req(List<VarianteRequest> variantes, boolean publicar) {
        return new ProductoRequest("Body manga larga", "Algodón", "c1", List.of(), "UNISEX", "M0_12", 34900L,
                variantes, publicar);
    }

    @Test
    void hu17_generaSkuConCodigoTallaYColor() {
        when(repo.countByCodigoStartingWith("BOD-")).thenReturn(0L);
        when(repo.existsByCodigo("BOD-001")).thenReturn(false);
        when(repo.existsByVariantesSku(anyString())).thenReturn(false);

        var dto = service.crear(req(List.of(new VarianteRequest("6m", "rosa")), true));

        assertThat(dto.codigo()).isEqualTo("BOD-001");
        assertThat(dto.variantes()).extracting("sku").containsExactly("BOD-001-6M-ROSA");
        assertThat(dto.estado()).isEqualTo("PUBLICADO");
    }

    @Test
    void hu22_guardarComoBorradorNoLoPublica() {
        when(repo.countByCodigoStartingWith("BOD-")).thenReturn(0L);
        when(repo.existsByVariantesSku(anyString())).thenReturn(false);

        var dto = service.crear(req(List.of(new VarianteRequest("6M", "Rosa")), false));

        assertThat(dto.estado()).isEqualTo("BORRADOR");
    }

    @Test
    void hu25_rechazaSkuExistenteEnOtroProducto() {
        when(repo.countByCodigoStartingWith("BOD-")).thenReturn(0L);
        when(repo.existsByVariantesSku("BOD-001-6M-ROSA")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(req(List.of(new VarianteRequest("6M", "Rosa")), true)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repo, never()).save(any());
    }

    @Test
    void hu25_rechazaVarianteRepetidaEnElMismoProducto() {
        when(repo.countByCodigoStartingWith("BOD-")).thenReturn(0L);
        when(repo.existsByVariantesSku(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.crear(req(List.of(new VarianteRequest("6M", "Rosa"),
                new VarianteRequest("6m", "ROSA")), true))).hasMessageContaining("repetida");
    }

    @Test
    void hu24_sinVariantesNoSeGuarda() {
        when(repo.countByCodigoStartingWith("BOD-")).thenReturn(0L);

        assertThatThrownBy(() -> service.crear(req(List.of(), true)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void hu17_varianteSinColorSeRechazaIndicandoElCampo() {
        when(repo.countByCodigoStartingWith("BOD-")).thenReturn(0L);

        assertThatThrownBy(() -> service.crear(req(List.of(new VarianteRequest("6M", " ")), true)))
                .hasMessageContaining("color");
    }

    @Test
    void hu20_productoDesactivadoNoSeMuestraAlCliente() {
        Producto p = producto();
        p.setActivo(false);
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.detallePublico("p1", false)).hasMessage("Producto no disponible");
        assertThat(service.detallePublico("p1", true).activo()).isFalse();
    }

    @Test
    void hu26_precioConDescuentoVigente() {
        Producto p = producto();
        p.setDescuento(new Descuento(20, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        var dto = service.detallePublico("p1", false);

        assertThat(dto.precio()).isEqualTo(50000);
        assertThat(dto.precioFinal()).isEqualTo(40000);
        assertThat(dto.descuentoPorcentaje()).isEqualTo(20);
    }

    @Test
    void hu23_descuentoVencidoNoSeAplica() {
        Producto p = producto();
        p.setDescuento(new Descuento(20, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        assertThat(service.detallePublico("p1", false).precioFinal()).isEqualTo(50000);
    }

    @Test
    void hu23_finAnteriorAlInicioSeRechaza() {
        assertThatThrownBy(() -> CategoriaService.validarDescuento(
                new DescuentoRequest(10, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 10))))
                .hasMessageContaining("anterior");
        assertThatThrownBy(() -> CategoriaService.validarDescuento(
                new DescuentoRequest(150, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10))))
                .hasMessageContaining("entre 1 y 100");
    }

    @Test
    void hu22_borradorSinVariantesNoSePuedePublicar() {
        Producto p = producto();
        p.setEstado(EstadoProducto.BORRADOR);
        p.setVariantes(new ArrayList<>());
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.publicar("p1")).hasMessageContaining("variante");
    }

    private Producto producto() {
        Producto p = new Producto();
        p.setId("p1");
        p.setCodigo("BOD-001");
        p.setNombre("Body");
        p.setCategoriaId("c1");
        p.setPrecio(50000);
        p.setEstado(EstadoProducto.PUBLICADO);
        p.setActivo(true);
        p.setVariantes(new ArrayList<>(List.of(new Variante("BOD-001-6M-ROSA", "6M", "Rosa"))));
        return p;
    }
}
