package com.minimundo.logistica.controller;

import com.minimundo.logistica.dto.LogisticaDtos.AsignarRepartidorRequest;
import com.minimundo.logistica.dto.LogisticaDtos.DespachoRequest;
import com.minimundo.logistica.dto.LogisticaDtos.EstadoEnvioRequest;
import com.minimundo.logistica.dto.LogisticaDtos.TomarPedidoRequest;
import com.minimundo.logistica.dto.LogisticaDtos.VerificacionRequest;
import com.minimundo.logistica.error.ApiException;
import com.minimundo.logistica.model.Envio;
import com.minimundo.logistica.security.CurrentUser;
import com.minimundo.logistica.service.EnvioService;
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
@RequestMapping("/api/envios")
@Tag(name = "Envíos", description = "Alistamiento, despacho y asignación de repartidor")
public class EnvioController {

    private final EnvioService servicio;

    public EnvioController(EnvioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN','ASESOR')")
    @Operation(summary = "Envíos por estado (EN_ALISTAMIENTO, DESPACHADO, EN_RUTA...) o con entrega fallida")
    public List<Envio> listar(@RequestParam(required = false) String estado,
                              @RequestParam(required = false) Boolean fallidas,
                              @RequestParam(required = false) String repartidorId) {
        return servicio.listar(estado, fallidas, repartidorId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN','ASESOR')")
    @Operation(summary = "Detalle de un envío")
    public Envio obtener(@PathVariable String id) {
        return servicio.obtener(id);
    }

    @GetMapping("/pedido/{pedidoId}")
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN','ASESOR','SERVICIO')")
    @Operation(summary = "Envío de un pedido")
    public Envio porPedido(@PathVariable String pedidoId) {
        return servicio.porPedido(pedidoId);
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-51 · Tomar un pedido PAGADO y marcarlo EN_ALISTAMIENTO")
    @ApiResponse(responseCode = "409", description = "El pedido ya fue tomado por otro operario")
    public Envio tomar(@Valid @RequestBody TomarPedidoRequest req) {
        return servicio.tomar(req.pedidoId(), CurrentUser.get());
    }

    @PatchMapping("/{id}/verificacion")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-52 · Verificar un ítem del pedido (SKU y cantidad)")
    public Envio verificar(@PathVariable String id, @Valid @RequestBody VerificacionRequest req) {
        return servicio.verificarItem(id, req.sku(), req.cantidad(), CurrentUser.get());
    }

    @PatchMapping("/{id}/alistamiento")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-52 · Confirmar el alistamiento (todos los ítems verificados)")
    public Envio confirmar(@PathVariable String id) {
        return servicio.confirmarAlistamiento(id, CurrentUser.get());
    }

    @PatchMapping("/{id}/despacho")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-53 · Registrar el despacho con transportadora y número de guía")
    @ApiResponse(responseCode = "409", description = "HU-57 · El pedido no está en alistamiento")
    public Envio despachar(@PathVariable String id, @Valid @RequestBody DespachoRequest req) {
        return servicio.despachar(id, req.transportadora(), req.guia(), CurrentUser.get());
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "Cambio de estado del envío (DESPACHADO con transportadora y guía)")
    public Envio estado(@PathVariable String id, @Valid @RequestBody EstadoEnvioRequest req) {
        if (!"DESPACHADO".equalsIgnoreCase(req.estado())) {
            throw ApiException.badRequest("Desde aquí solo se registra el estado DESPACHADO");
        }
        return servicio.despachar(id, req.transportadora(), req.guia(), CurrentUser.get());
    }

    @PatchMapping("/{id}/repartidor")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-106 · Asignar (o reasignar) un pedido despachado a un repartidor activo")
    public Envio asignar(@PathVariable String id, @Valid @RequestBody AsignarRepartidorRequest req) {
        return servicio.asignarRepartidor(id, req.repartidorId(), CurrentUser.get());
    }
}
