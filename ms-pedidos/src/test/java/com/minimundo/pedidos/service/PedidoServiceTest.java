package com.minimundo.pedidos.service;

import com.minimundo.pedidos.dto.PedidoDtos.CambioEstadoRequest;
import com.minimundo.pedidos.dto.PedidoDtos.CheckoutRequest;
import com.minimundo.pedidos.dto.PedidoDtos.DireccionRequest;
import com.minimundo.pedidos.dto.PedidoDtos.PagoRequest;
import com.minimundo.pedidos.dto.PedidoDtos.PrecioSku;
import com.minimundo.pedidos.error.ApiException;
import com.minimundo.pedidos.model.Carrito;
import com.minimundo.pedidos.model.EstadoPedido;
import com.minimundo.pedidos.model.ItemCarrito;
import com.minimundo.pedidos.model.Pedido;
import com.minimundo.pedidos.model.TarifaEnvio;
import com.minimundo.pedidos.repository.PedidoRepository;
import com.minimundo.pedidos.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock PedidoRepository repo;
    @Mock MongoTemplate mongo;
    @Mock CarritoService carritos;
    @Mock TarifaService tarifas;
    @Mock Integraciones integraciones;
    PedidoService service;

    AuthUser cliente = new AuthUser("c1", "laura@correo.com", "Laura", "CLIENTE");

    @BeforeEach
    void setUp() {
        service = new PedidoService(repo, mongo, carritos, tarifas, integraciones, new PagoSimulado());
        lenient().when(repo.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId("p1");
            }
            return p;
        });
    }

    private void prepararCarrito() {
        Carrito c = new Carrito();
        c.setClienteId("c1");
        ItemCarrito i = new ItemCarrito();
        i.setSku("BOD-001-6M-ROSA");
        i.setNombre("Body");
        i.setCantidad(2);
        c.setItems(new ArrayList<>(List.of(i)));
        when(carritos.buscar("c1")).thenReturn(Optional.of(c));
        TarifaEnvio t = new TarifaEnvio();
        t.setCiudad("Bucaramanga");
        t.setCosto(8000);
        when(tarifas.porCiudad("Bucaramanga")).thenReturn(Optional.of(t));
        when(integraciones.precios(List.of("BOD-001-6M-ROSA"))).thenReturn(Map.of("BOD-001-6M-ROSA",
                new PrecioSku("BOD-001-6M-ROSA", "prod1", "Body", "6M", "Rosa", 50000, 40000, 20, true, null)));
        when(repo.findFirstByClienteIdAndEstadoOrderByFechaCreacionDesc("c1", EstadoPedido.CREADO))
                .thenReturn(Optional.empty());
    }

    private CheckoutRequest req(String tarjeta) {
        return new CheckoutRequest(new DireccionRequest("Laura", "Calle 45 # 27-30", "Cabecera", "Bucaramanga",
                "3001234567", null), new PagoRequest("Laura", tarjeta, "12/30", "123"));
    }

    @Test
    void hu39_pagoAprobadoDejaElPedidoPagadoYVaciaElCarrito() {
        prepararCarrito();

        var dto = service.checkout(cliente, req("4242424242424242"));

        assertThat(dto.estado()).isEqualTo("PAGADO");
        assertThat(dto.codigo()).startsWith("MM-");
        assertThat(dto.subtotal()).isEqualTo(100000);
        assertThat(dto.descuentos()).isEqualTo(20000);
        assertThat(dto.total()).isEqualTo(100000 - 20000 + 8000);
        verify(integraciones).reservar(any());
        verify(carritos).vaciar("c1");
    }

    @Test
    void hu40_pagoRechazadoLiberaLaReservaYConservaElCarrito() {
        prepararCarrito();

        assertThatThrownBy(() -> service.checkout(cliente, req("4000000000000002")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED))
                .hasMessageContaining("Fondos insuficientes");
        verify(integraciones).liberar("p1");
        verify(carritos, never()).vaciar(any());
    }

    @Test
    void hu36_sinStockSuficienteNoSeCreaElPedido() {
        prepararCarrito();
        doThrow(ApiException.conflict("No hay stock suficiente de: Body talla 6M color Rosa (disponibles: 1)"))
                .when(integraciones).reservar(any());

        assertThatThrownBy(() -> service.checkout(cliente, req("4242424242424242")))
                .hasMessageContaining("talla 6M color Rosa");
        verify(repo).delete(any(Pedido.class));
        verify(carritos, never()).vaciar(any());
    }

    @Test
    void hu38_ciudadSinCoberturaNoPuedeContinuar() {
        Carrito c = new Carrito();
        c.setItems(new ArrayList<>(List.of(new ItemCarrito())));
        when(carritos.buscar("c1")).thenReturn(Optional.of(c));
        when(tarifas.porCiudad("Bucaramanga")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(cliente, req("4242424242424242")))
                .hasMessageContaining("No hay cobertura");
    }

    @Test
    void hu44_noSeCancelaUnPedidoDespachado() {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setClienteId("c1");
        p.setEstado(EstadoPedido.DESPACHADO);
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancelar("p1", cliente))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(integraciones, never()).liberar(any());
    }

    @Test
    void hu44_cancelarPedidoPagadoLiberaElStock() {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setClienteId("c1");
        p.setEstado(EstadoPedido.PAGADO);
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        var dto = service.cancelar("p1", cliente);

        assertThat(dto.estado()).isEqualTo("CANCELADO");
        assertThat(dto.historialEstados()).isNotEmpty();
        verify(integraciones).liberar("p1");
    }

    @Test
    void hu42_clienteNoVePedidosAjenos() {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setClienteId("otro");
        p.setEstado(EstadoPedido.PAGADO);
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.detalle("p1", cliente))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void hu111_soloEnRutaSeMuestraElRepartidorAlCliente() {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setClienteId("c1");
        p.setEstado(EstadoPedido.DESPACHADO);
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        service.cambiarEstado("p1", new CambioEstadoRequest("EN_RUTA", "r1", "Jhon", null, null, null, "Jhon"));
        assertThat(service.detalle("p1", cliente).repartidorNombre()).isEqualTo("Jhon");

        service.cambiarEstado("p1", new CambioEstadoRequest("DESPACHADO", "r1", "Jhon", "Entrega fallida", null, null, null));
        assertThat(service.detalle("p1", cliente).repartidorNombre()).isNull();
    }

    @Test
    void transicionInvalidaSeRechazaCon409() {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setEstado(EstadoPedido.PAGADO);
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cambiarEstado("p1",
                new CambioEstadoRequest("ENTREGADO", "u", "U", null, null, null, null)))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
