package com.minimundo.atencion.controller;

import com.minimundo.atencion.dto.AtencionDtos.DecisionRequest;
import com.minimundo.atencion.dto.AtencionDtos.ReingresoMarca;
import com.minimundo.atencion.dto.AtencionDtos.SolicitudDto;
import com.minimundo.atencion.dto.AtencionDtos.SolicitudRequest;
import com.minimundo.atencion.security.CurrentUser;
import com.minimundo.atencion.service.SolicitudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devoluciones")
@Tag(name = "Cambios y devoluciones", description = "Solicitudes de cambio de talla y devolución")
public class SolicitudController {

    private final SolicitudService servicio;

    public SolicitudController(SolicitudService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-64/HU-65 · Solicitar cambio de talla o devolución de un pedido entregado")
    @ApiResponse(responseCode = "409", description = "HU-66 · Fuera del plazo o talla deseada agotada")
    public SolicitudDto crear(@Valid @RequestBody SolicitudRequest req) {
        return servicio.crear(req, CurrentUser.get());
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "HU-67 · Mis solicitudes con su estado y motivo de rechazo")
    public List<SolicitudDto> mias() {
        return servicio.mias(CurrentUser.get().id());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ASESOR','ADMIN','OPERARIO')")
    @Operation(summary = "Solicitudes por estado (el operario ve las APROBADAS pendientes de reingreso)")
    public List<SolicitudDto> listar(@RequestParam(required = false) String estado,
                                     @RequestParam(required = false) Boolean reingresada,
                                     @RequestParam(required = false) String clienteId) {
        return servicio.listar(estado, reingresada, clienteId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE','ASESOR','ADMIN','OPERARIO')")
    @Operation(summary = "Detalle de una solicitud")
    public SolicitudDto detalle(@PathVariable String id) {
        return servicio.detalle(id, CurrentUser.get());
    }

    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasRole('ASESOR')")
    @Operation(summary = "HU-72/HU-73 · Aprobar o rechazar (con motivo) una solicitud pendiente")
    public SolicitudDto decidir(@PathVariable String id, @Valid @RequestBody DecisionRequest req) {
        return servicio.decidir(id, req.aprobar(), req.motivo(), CurrentUser.get());
    }

    @GetMapping("/interno/{id}")
    @PreAuthorize("hasRole('SERVICIO')")
    @Operation(summary = "Interno · Solicitud para el reingreso en ms-logistica")
    public SolicitudDto interno(@PathVariable String id) {
        return servicio.dto(servicio.obtener(id));
    }

    @PatchMapping("/interno/{id}/reingreso")
    @PreAuthorize("hasRole('SERVICIO')")
    @Operation(summary = "Interno · HU-59 · Marcar la mercancía como reingresada")
    public SolicitudDto reingreso(@PathVariable String id, @RequestBody ReingresoMarca req) {
        return servicio.marcarReingreso(id, req.usuarioNombre());
    }
}
