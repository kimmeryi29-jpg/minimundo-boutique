package com.minimundo.atencion.controller;

import com.minimundo.atencion.dto.AtencionDtos.PqrsDto;
import com.minimundo.atencion.dto.AtencionDtos.RadicarPqrsRequest;
import com.minimundo.atencion.dto.AtencionDtos.ReportePqrs;
import com.minimundo.atencion.dto.AtencionDtos.RespuestaRequest;
import com.minimundo.atencion.security.CurrentUser;
import com.minimundo.atencion.service.PqrsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pqrs")
@Tag(name = "PQRS", description = "Peticiones, quejas, reclamos y sugerencias")
public class PqrsController {

    private final PqrsService servicio;

    public PqrsController(PqrsService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-61/HU-62 · Radicar una PQRS (opcionalmente asociada a un pedido propio)")
    public PqrsDto radicar(@Valid @RequestBody RadicarPqrsRequest req) {
        return servicio.radicar(req, CurrentUser.get());
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "HU-63 · Mis PQRS con su estado y respuesta")
    public List<PqrsDto> mias() {
        return servicio.misPqrs(CurrentUser.get().id());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ASESOR','ADMIN')")
    @Operation(summary = "HU-68 · Bandeja de PQRS filtrada por estado y vencimiento")
    @ApiResponse(responseCode = "403", description = "Usuario sin rol ASESOR")
    public List<PqrsDto> bandeja(@RequestParam(required = false) String estado,
                                 @RequestParam(required = false) Boolean vencidas,
                                 @RequestParam(required = false) String asesorId) {
        return servicio.bandeja(estado, vencidas, asesorId);
    }

    @GetMapping("/reporte")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-85 · Reporte de PQRS por tipo y estado")
    public ReportePqrs reporte(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.reporte(desde, hasta);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE','ASESOR','ADMIN')")
    @Operation(summary = "HU-75 · Detalle de una PQRS con su historial de estados")
    public PqrsDto detalle(@PathVariable String id) {
        return servicio.detalle(id, CurrentUser.get());
    }

    @PatchMapping("/{id}/tomar")
    @PreAuthorize("hasRole('ASESOR')")
    @Operation(summary = "HU-69 · Tomar una PQRS radicada y asignármela")
    @ApiResponse(responseCode = "409", description = "Ya asignada a otro asesor")
    public PqrsDto tomar(@PathVariable String id) {
        return servicio.tomar(id, CurrentUser.get());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ASESOR')")
    @Operation(summary = "HU-70 · Registrar la respuesta y cerrar la PQRS")
    public PqrsDto responder(@PathVariable String id, @Valid @RequestBody RespuestaRequest req) {
        return servicio.responder(id, req.respuesta(), CurrentUser.get());
    }
}
