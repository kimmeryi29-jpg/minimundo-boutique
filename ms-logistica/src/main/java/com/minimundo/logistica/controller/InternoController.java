package com.minimundo.logistica.controller;

import com.minimundo.logistica.dto.LogisticaDtos.ReservaRequest;
import com.minimundo.logistica.dto.LogisticaDtos.ReservaResponse;
import com.minimundo.logistica.model.Reserva;
import com.minimundo.logistica.service.EnvioService;
import com.minimundo.logistica.service.InventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reservas de stock: las invoca ms-pedidos con su token de servicio (tabla 11: "Interno (ms-pedidos)"). */
@RestController
@RequestMapping("/api/inventario/reservas")
@PreAuthorize("hasRole('SERVICIO')")
@Tag(name = "Interno", description = "Reserva y liberación de stock para el checkout")
public class InternoController {

    private final InventarioService inventario;
    private final EnvioService envios;

    public InternoController(InventarioService inventario, EnvioService envios) {
        this.inventario = inventario;
        this.envios = envios;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-54 · Reservar el stock de un pedido (todo o nada, actualización atómica)")
    @ApiResponse(responseCode = "409", description = "HU-36 · Alguna variante no tiene stock suficiente")
    public ReservaResponse reservar(@Valid @RequestBody ReservaRequest req) {
        Reserva r = inventario.reservar(req);
        return new ReservaResponse(r.getPedidoId(), r.getEstado().name());
    }

    @PostMapping("/{pedidoId}/liberar")
    @Operation(summary = "HU-55 · Liberar la reserva (pago rechazado, falla del checkout o cancelación)")
    public ReservaResponse liberar(@PathVariable String pedidoId) {
        envios.cancelarPorPedido(pedidoId);
        inventario.liberar(pedidoId);
        return new ReservaResponse(pedidoId, "LIBERADA");
    }
}
