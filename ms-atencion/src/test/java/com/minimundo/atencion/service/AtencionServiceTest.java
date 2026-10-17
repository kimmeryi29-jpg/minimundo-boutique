package com.minimundo.atencion.service;

import com.minimundo.atencion.client.ServiceClient;
import com.minimundo.atencion.dto.AtencionDtos.ItemPedido;
import com.minimundo.atencion.dto.AtencionDtos.PedidoInfo;
import com.minimundo.atencion.dto.AtencionDtos.SolicitudRequest;
import com.minimundo.atencion.dto.AtencionDtos.VarianteInfo;
import com.minimundo.atencion.error.ApiException;
import com.minimundo.atencion.model.EstadoPqrs;
import com.minimundo.atencion.model.EstadoSolicitud;
import com.minimundo.atencion.model.Parametros;
import com.minimundo.atencion.model.Pqrs;
import com.minimundo.atencion.model.Solicitud;
import com.minimundo.atencion.model.TipoPqrs;
import com.minimundo.atencion.model.TipoSolicitud;
import com.minimundo.atencion.repository.PqrsRepository;
import com.minimundo.atencion.repository.SolicitudRepository;
import com.minimundo.atencion.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtencionServiceTest {

    // Viernes 16 de octubre de 2026, 10:00 a. m. en Bogotá
    Clock clock = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);

    @Mock PqrsRepository pqrsRepo;
    @Mock SolicitudRepository solRepo;
    @Mock MongoTemplate mongo;
    @Mock ParametrosService parametros;
    @Mock Consecutivos consecutivos;
    @Mock ServiceClient pedidos;
    @Mock ServiceClient catalogo;
    @Mock ServiceClient logistica;

    PqrsService pqrs;
    SolicitudService solicitudes;
    AuthUser cliente = new AuthUser("c1", "laura@correo.com", "Laura", "CLIENTE");
    AuthUser asesor = new AuthUser("a1", "asesor@minimundo.co", "Andrés", "ASESOR");

    @BeforeEach
    void setUp() {
        pqrs = new PqrsService(pqrsRepo, mongo, parametros, consecutivos, pedidos, clock);
        solicitudes = new SolicitudService(solRepo, mongo, parametros, consecutivos, pedidos, catalogo, logistica, clock);
        lenient().when(parametros.obtener()).thenReturn(new Parametros());
        lenient().when(solRepo.save(any(Solicitud.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(pqrsRepo.save(any(Pqrs.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void hu74_diasHabilesNoCuentanFinesDeSemana() {
        // Del viernes 9 al viernes 16 de octubre: 5 días hábiles
        assertThat(DiasHabiles.entre(Instant.parse("2026-10-09T15:00:00Z"), Instant.parse("2026-10-16T15:00:00Z")))
                .isEqualTo(5);
    }

    @Test
    void hu74_pqrsConMasDeCincoDiasHabilesSeMarcaVencidaYCerradaNunca() {
        Pqrs p = new Pqrs();
        p.setTipo(TipoPqrs.QUEJA);
        p.setEstado(EstadoPqrs.RADICADA);
        p.setFechaRadicacion(Instant.parse("2026-10-07T15:00:00Z"));
        assertThat(pqrs.dto(p).vencida()).isTrue();

        p.setEstado(EstadoPqrs.CERRADA);
        p.setFechaCierre(Instant.parse("2026-10-16T15:00:00Z"));
        assertThat(pqrs.dto(p).vencida()).isFalse();
    }

    @Test
    void hu69_noSeTomaUnaPqrsAsignadaAOtroAsesor() {
        Pqrs p = new Pqrs();
        p.setId("q1");
        p.setEstado(EstadoPqrs.EN_GESTION);
        p.setAsesorNombre("Otro asesor");
        when(pqrsRepo.findById("q1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> pqrs.tomar("q1", asesor)).hasMessageContaining("ya está asignada");
    }

    @Test
    void hu70_respuestaVaciaNoCierraLaPqrs() {
        assertThatThrownBy(() -> pqrs.responder("q1", "  ", asesor))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private PedidoInfo pedido(String estado, Instant entrega) {
        return new PedidoInfo("p1", "MM-1", "c1", "Laura", estado,
                List.of(new ItemPedido("BOD-001-6M-ROSA", "prod1", "Body", "6M", "Rosa", 1)), entrega);
    }

    @Test
    void hu64_noSeSolicitaCambioDeUnPedidoNoEntregado() {
        when(pedidos.get(anyString(), eq(PedidoInfo.class), any(Object[].class))).thenReturn(pedido("DESPACHADO", null));

        assertThatThrownBy(() -> solicitudes.crear(new SolicitudRequest("CAMBIO", "p1", "BOD-001-6M-ROSA", 1,
                "9M", "Le quedó pequeño"), cliente)).hasMessageContaining("entregados");
    }

    @Test
    void hu66_fueraDelPlazoSeRechazaEInformaElPlazo() {
        when(pedidos.get(anyString(), eq(PedidoInfo.class), any(Object[].class)))
                .thenReturn(pedido("ENTREGADO", Instant.now(clock).minus(40, ChronoUnit.DAYS)));

        assertThatThrownBy(() -> solicitudes.crear(new SolicitudRequest("DEVOLUCION", "p1", "BOD-001-6M-ROSA", 1,
                null, "Defecto"), cliente)).hasMessageContaining("30 días");
    }

    @Test
    void hu65_devolucionDentroDelPlazoQuedaPendiente() {
        when(pedidos.get(anyString(), eq(PedidoInfo.class), any(Object[].class)))
                .thenReturn(pedido("ENTREGADO", Instant.now(clock).minus(2, ChronoUnit.DAYS)));
        when(consecutivos.siguiente("SOL")).thenReturn("SOL-000001");

        var dto = solicitudes.crear(new SolicitudRequest("DEVOLUCION", "p1", "BOD-001-6M-ROSA", 1, null, "Defecto"),
                cliente);

        assertThat(dto.estado()).isEqualTo("PENDIENTE");
        assertThat(dto.numero()).isEqualTo("SOL-000001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void hu64_tallaDeseadaAgotadaSeInformaAlCliente() {
        when(pedidos.get(anyString(), eq(PedidoInfo.class), any(Object[].class)))
                .thenReturn(pedido("ENTREGADO", Instant.now(clock).minus(1, ChronoUnit.DAYS)));
        when(catalogo.get(anyString(), eq(VarianteInfo.class), any(Object[].class)))
                .thenReturn(new VarianteInfo("BOD-001-9M-ROSA", "prod1", "Body", "BOD-001", "9M", "Rosa", true, true));
        when(logistica.get(anyString(), any(ParameterizedTypeReference.class), any(Object[].class)))
                .thenReturn(Map.of("BOD-001-9M-ROSA", 0));

        assertThatThrownBy(() -> solicitudes.crear(new SolicitudRequest("CAMBIO", "p1", "BOD-001-6M-ROSA", 1, "9M",
                "Le quedó pequeño"), cliente)).hasMessageContaining("agotada");
        verify(solRepo, never()).save(any());
    }

    @Test
    void hu73_rechazoSinMotivoNoSePermite() {
        Solicitud s = new Solicitud();
        s.setId("s1");
        s.setTipo(TipoSolicitud.DEVOLUCION);
        s.setEstado(EstadoSolicitud.PENDIENTE);
        when(solRepo.findById("s1")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> solicitudes.decidir("s1", false, " ", asesor)).hasMessageContaining("motivo");
    }

    @Test
    void hu72_solicitudYaDecididaNoSeApruebaDeNuevo() {
        Solicitud s = new Solicitud();
        s.setId("s1");
        s.setTipo(TipoSolicitud.DEVOLUCION);
        s.setEstado(EstadoSolicitud.APROBADA);
        when(solRepo.findById("s1")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> solicitudes.decidir("s1", true, null, asesor))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void hu59_reingresoSoloUnaVez() {
        Solicitud s = new Solicitud();
        s.setId("s1");
        s.setTipo(TipoSolicitud.DEVOLUCION);
        s.setEstado(EstadoSolicitud.APROBADA);
        s.setReingresada(true);
        when(solRepo.findById("s1")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> solicitudes.marcarReingreso("s1", "Camila")).hasMessageContaining("ya fue reingresada");
    }
}
