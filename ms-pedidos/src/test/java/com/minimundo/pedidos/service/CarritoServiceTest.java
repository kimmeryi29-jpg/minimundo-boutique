package com.minimundo.pedidos.service;

import com.minimundo.pedidos.dto.PedidoDtos.PrecioSku;
import com.minimundo.pedidos.error.ApiException;
import com.minimundo.pedidos.model.Carrito;
import com.minimundo.pedidos.model.ItemCarrito;
import com.minimundo.pedidos.repository.CarritoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    @Mock CarritoRepository repo;
    @Mock Integraciones integraciones;
    @Mock TarifaService tarifas;
    CarritoService service;
    Carrito carrito;

    static final String SKU = "BOD-001-6M-ROSA";

    @BeforeEach
    void setUp() {
        service = new CarritoService(repo, integraciones, tarifas);
        carrito = new Carrito();
        carrito.setClienteId("c1");
        lenient().when(repo.findByClienteId("c1")).thenAnswer(i -> Optional.of(carrito));
        lenient().when(repo.save(any(Carrito.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(integraciones.precios(anyList())).thenReturn(Map.of(SKU,
                new PrecioSku(SKU, "p1", "Body", "6M", "Rosa", 50000, 50000, 0, true, null)));
    }

    @Test
    void hu31_mismoSkuSeSumaEnLugarDeDuplicarLaLinea() {
        when(integraciones.disponibilidad(anyList())).thenReturn(Map.of(SKU, 10));

        service.agregar("c1", SKU, 2, null);
        service.agregar("c1", SKU, 1, null);

        assertThat(carrito.getItems()).hasSize(1);
        assertThat(carrito.getItems().get(0).getCantidad()).isEqualTo(3);
    }

    @Test
    void hu35_noSeAgregaMasQueElDisponible() {
        when(integraciones.disponibilidad(anyList())).thenReturn(Map.of(SKU, 2));

        assertThatThrownBy(() -> service.agregar("c1", SKU, 3, null))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Solo hay 2");
        verify(repo, never()).save(any());
    }

    @Test
    void hu30_varianteAgotadaNoSePuedeAgregar() {
        when(integraciones.disponibilidad(anyList())).thenReturn(Map.of(SKU, 0));

        assertThatThrownBy(() -> service.agregar("c1", SKU, 1, null)).hasMessageContaining("Agotado");
    }

    @Test
    void hu32_cantidadMenorAUnoSeRechaza() {
        assertThatThrownBy(() -> service.cambiarCantidad("c1", SKU, 0, null))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void hu34_totalEsSubtotalMenosDescuentosMasEnvio() {
        ItemCarrito i = new ItemCarrito();
        i.setSku(SKU);
        i.setNombre("Body");
        i.setCantidad(2);
        carrito.setItems(new ArrayList<>(List.of(i)));
        when(integraciones.precios(anyList())).thenReturn(Map.of(SKU,
                new PrecioSku(SKU, "p1", "Body", "6M", "Rosa", 50000, 45000, 10, true, null)));
        when(integraciones.disponibilidad(anyList())).thenReturn(Map.of(SKU, 5));

        var sinCiudad = service.resumen("c1", null);
        assertThat(sinCiudad.envioPendiente()).isTrue();
        assertThat(sinCiudad.total()).isEqualTo(90000);
        assertThat(sinCiudad.descuentos()).isEqualTo(10000);
    }

    @Test
    void hu33_alQuitarElUltimoProductoElCarritoQuedaVacio() {
        ItemCarrito i = new ItemCarrito();
        i.setSku(SKU);
        i.setCantidad(1);
        carrito.setItems(new ArrayList<>(List.of(i)));

        var r = service.quitar("c1", SKU, null);

        assertThat(r.items()).isEmpty();
    }
}
