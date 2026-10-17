package com.minimundo.logistica.controller;

import com.minimundo.logistica.dto.LogisticaDtos.EntregaFallidaRequest;
import com.minimundo.logistica.dto.LogisticaDtos.HistorialEntrega;
import com.minimundo.logistica.model.Envio;
import com.minimundo.logistica.security.CurrentUser;
import com.minimundo.logistica.service.EnvioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/entregas")
@PreAuthorize("hasRole('REPARTIDOR')")
@Tag(name = "Entregas", description = "Vista del repartidor: mis entregas, en camino, entrega y novedades")
public class EntregaController {

    private final EnvioService servicio;

    public EntregaController(EnvioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/mis-entregas")
    @Operation(summary = "HU-107 · Pedidos asignados al repartidor con su dirección de entrega")
    public List<Envio> misEntregas() {
        return servicio.misEntregas(CurrentUser.get());
    }

    @GetMapping("/historial")
    @Operation(summary = "HU-110 · Historial de entregas realizadas y fallidas")
    public List<HistorialEntrega> historial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.historial(CurrentUser.get(), desde, hasta);
    }

    @PatchMapping("/{id}/en-ruta")
    @Operation(summary = "HU-108 · Marcar el pedido como en camino")
    @ApiResponse(responseCode = "403", description = "HU-112 · Pedido asignado a otro repartidor")
    public Envio enRuta(@PathVariable String id) {
        return servicio.marcarEnRuta(id, CurrentUser.get());
    }

    @PatchMapping("/{id}/entrega")
    @Operation(summary = "HU-58 · Registrar la entrega al cliente")
    public Envio entrega(@PathVariable String id) {
        return servicio.registrarEntrega(id, CurrentUser.get());
    }

    @PatchMapping("/{id}/fallida")
    @Operation(summary = "HU-109 · Reportar una entrega fallida con su motivo")
    public Envio fallida(@PathVariable String id, @Valid @RequestBody EntregaFallidaRequest req) {
        return servicio.reportarFallida(id, req.motivo(), req.detalle(), CurrentUser.get());
    }
}
