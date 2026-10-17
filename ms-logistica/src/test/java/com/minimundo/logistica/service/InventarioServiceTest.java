package com.minimundo.logistica.service;

import com.minimundo.logistica.client.ServiceClient;
import com.minimundo.logistica.dto.LogisticaDtos.AjusteRequest;
import com.minimundo.logistica.dto.LogisticaDtos.EntradaRequest;
import com.minimundo.logistica.dto.LogisticaDtos.ItemReservaRequest;
import com.minimundo.logistica.dto.LogisticaDtos.ReservaRequest;
import com.minimundo.logistica.error.ApiException;
import com.minimundo.logistica.model.Inventario;
import com.minimundo.logistica.repository.InventarioRepository;
import com.minimundo.logistica.repository.MovimientoRepository;
import com.minimundo.logistica.repository.ParametrosRepository;
import com.minimundo.logistica.repository.ReservaRepository;
import com.minimundo.logistica.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

    @Mock InventarioRepository repo;
    @Mock MovimientoRepository movimientos;
    @Mock ReservaRepository reservas;
    @Mock ParametrosRepository parametros;
    @Mock MongoTemplate mongo;
    @Mock ServiceClient catalogo;
    @Mock ServiceClient atencion;
    InventarioService service;
    AuthUser operario = new AuthUser("op1", "op@minimundo.co", "Camila", "OPERARIO");

    @BeforeEach
    void setUp() {
        service = new InventarioService(repo, movimientos, reservas, parametros, mongo, catalogo, atencion);
    }

    @Test
    void hu46_cantidadMenorOIgualACeroSeRechaza() {
        assertThatThrownBy(() -> service.registrarEntrada(new EntradaRequest("BOD-001-6M-ROSA", 0, null), operario))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void hu48_ajusteSinMotivoNoSePermite() {
        assertThatThrownBy(() -> service.registrarAjuste(new AjusteRequest("BOD-001-6M-ROSA", -1, " "), operario))
                .hasMessageContaining("motivo");
    }

    @Test
    void hu54_siUnItemNoTieneStockSeLiberanLosYaReservados() {
        when(reservas.findByPedidoId("p1")).thenReturn(Optional.empty());
        Inventario ok = new Inventario();
        ok.setSku("A");
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Inventario.class))).thenReturn(ok).thenReturn(null);
        when(repo.findBySku("B")).thenReturn(Optional.empty());

        var req = new ReservaRequest("p1", "MM-1", List.of(new ItemReservaRequest("A", "Body", "6M", "Rosa", 1),
                new ItemReservaRequest("B", "Vestido", "4", "Lila", 1)));

        assertThatThrownBy(() -> service.reservar(req))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Vestido talla 4 color Lila");
        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Inventario.class));
        verify(reservas, never()).save(any());
    }

    @Test
    void hu54_reservaExitosaQuedaActiva() {
        when(reservas.findByPedidoId("p1")).thenReturn(Optional.empty());
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Inventario.class))).thenReturn(new Inventario());
        when(reservas.save(any())).thenAnswer(i -> i.getArgument(0));

        var r = service.reservar(new ReservaRequest("p1", "MM-1",
                List.of(new ItemReservaRequest("A", "Body", "6M", "Rosa", 2))));

        assertThat(r.getEstado().name()).isEqualTo("ACTIVA");
        assertThat(r.getItems()).hasSize(1);
    }
}
