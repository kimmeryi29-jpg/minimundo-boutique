package com.minimundo.logistica.service;

import com.minimundo.logistica.client.ServiceClient;
import com.minimundo.logistica.error.ApiException;
import com.minimundo.logistica.model.Envio;
import com.minimundo.logistica.model.EstadoEnvio;
import com.minimundo.logistica.model.ItemEnvio;
import com.minimundo.logistica.repository.EnvioRepository;
import com.minimundo.logistica.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvioServiceTest {

    @Mock EnvioRepository repo;
    @Mock MongoTemplate mongo;
    @Mock InventarioService inventario;
    @Mock ServiceClient pedidos;
    @Mock ServiceClient usuarios;
    EnvioService service;

    AuthUser operario = new AuthUser("op1", "op@minimundo.co", "Camila", "OPERARIO");
    AuthUser repartidor = new AuthUser("r1", "rep@minimundo.co", "Jhon", "REPARTIDOR");
    AuthUser otroRepartidor = new AuthUser("r2", "rep2@minimundo.co", "Sergio", "REPARTIDOR");

    @BeforeEach
    void setUp() {
        service = new EnvioService(repo, mongo, inventario, pedidos, usuarios);
        lenient().when(repo.save(any(Envio.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Envio envio(EstadoEnvio estado) {
        Envio e = new Envio();
        e.setId("e1");
        e.setPedidoId("p1");
        e.setPedidoCodigo("MM-001");
        e.setEstado(estado);
        e.setOperarioId("op1");
        ItemEnvio i = new ItemEnvio();
        i.setSku("BOD-001-6M-ROSA");
        i.setCantidad(2);
        e.setItems(new ArrayList<>(List.of(i)));
        return e;
    }

    @Test
    void hu57_noSePuedeDespacharSiNoEstaEnAlistamiento() {
        Envio e = envio(EstadoEnvio.CANCELADO);
        when(repo.findById("e1")).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.despachar("e1", "Servientrega", "123", operario))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(inventario);
        verify(repo, never()).save(any());
    }

    @Test
    void hu53_transportadoraOGuiaVaciasNoRegistranElDespacho() {
        assertThatThrownBy(() -> service.despachar("e1", " ", "123", operario))
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void hu53_despachoExitosoDescuentaInventarioYActualizaPedido() {
        Envio e = envio(EstadoEnvio.EN_ALISTAMIENTO);
        e.setAlistamientoConfirmado(true);
        when(repo.findById("e1")).thenReturn(Optional.of(e));

        Envio r = service.despachar("e1", "Servientrega", "GU-123", operario);

        assertThat(r.getEstado()).isEqualTo(EstadoEnvio.DESPACHADO);
        assertThat(r.getGuia()).isEqualTo("GU-123");
        verify(inventario).consumirReserva("p1", "MM-001", operario);
        verify(pedidos).patch(eq("/api/pedidos/interno/{id}/estado"), any(), eq(Object.class), eq("p1"));
    }

    @Test
    void hu103_siPedidosNoRespondeSeRevierteElDescuento() {
        Envio e = envio(EstadoEnvio.EN_ALISTAMIENTO);
        e.setAlistamientoConfirmado(true);
        when(repo.findById("e1")).thenReturn(Optional.of(e));
        when(pedidos.patch(anyString(), any(), any(), any())).thenThrow(ApiException.unavailable("caído"));

        assertThatThrownBy(() -> service.despachar("e1", "Servientrega", "GU-1", operario))
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(inventario).revertirConsumoDePedido("p1");
        assertThat(e.getEstado()).isEqualTo(EstadoEnvio.EN_ALISTAMIENTO);
    }

    @Test
    void hu52_noSeConfirmaConItemsSinVerificar() {
        when(repo.findById("e1")).thenReturn(Optional.of(envio(EstadoEnvio.EN_ALISTAMIENTO)));

        assertThatThrownBy(() -> service.confirmarAlistamiento("e1", operario)).hasMessageContaining("por verificar");
    }

    @Test
    void hu52_verificacionConCantidadDistintaSeRechaza() {
        when(repo.findById("e1")).thenReturn(Optional.of(envio(EstadoEnvio.EN_ALISTAMIENTO)));

        assertThatThrownBy(() -> service.verificarItem("e1", "BOD-001-6M-ROSA", 1, operario))
                .hasMessageContaining("no coincide");
    }

    @Test
    void hu112_repartidorNoPuedeGestionarPedidoAjeno() {
        Envio e = envio(EstadoEnvio.DESPACHADO);
        e.setRepartidorId("r1");
        when(repo.findById("e1")).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.marcarEnRuta("e1", otroRepartidor))
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void hu112_sinRepartidorAsignadoNoSePuedeMarcarEnCamino() {
        when(repo.findById("e1")).thenReturn(Optional.of(envio(EstadoEnvio.DESPACHADO)));

        assertThatThrownBy(() -> service.marcarEnRuta("e1", repartidor)).hasMessageContaining("no tiene un repartidor");
    }

    @Test
    void hu108_y_hu58_enCaminoYEntrega() {
        Envio e = envio(EstadoEnvio.DESPACHADO);
        e.setRepartidorId("r1");
        when(repo.findById("e1")).thenReturn(Optional.of(e));

        service.marcarEnRuta("e1", repartidor);
        assertThat(e.getEstado()).isEqualTo(EstadoEnvio.EN_RUTA);
        service.registrarEntrega("e1", repartidor);
        assertThat(e.getEstado()).isEqualTo(EstadoEnvio.ENTREGADO);
        assertThat(e.getFechaEntrega()).isNotNull();
    }

    @Test
    void hu58_noSeEntregaUnPedidoQueNoEstaEnRuta() {
        Envio e = envio(EstadoEnvio.DESPACHADO);
        e.setRepartidorId("r1");
        when(repo.findById("e1")).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.registrarEntrega("e1", repartidor)).hasMessageContaining("EN_RUTA");
    }

    @Test
    void hu109_entregaFallidaVuelveADespachadoYQuedaRegistrada() {
        Envio e = envio(EstadoEnvio.EN_RUTA);
        e.setRepartidorId("r1");
        when(repo.findById("e1")).thenReturn(Optional.of(e));

        service.reportarFallida("e1", "CLIENTE_AUSENTE", null, repartidor);

        assertThat(e.getEstado()).isEqualTo(EstadoEnvio.DESPACHADO);
        assertThat(e.isEntregaFallida()).isTrue();
        assertThat(e.getEventos()).hasSize(1);
    }

    @Test
    void hu109_reporteSinMotivoNoSeRegistra() {
        assertThatThrownBy(() -> service.reportarFallida("e1", "", null, repartidor)).hasMessageContaining("motivo");
    }
}
