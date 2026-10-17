package com.minimundo.pedidos.controller;

import com.minimundo.pedidos.dto.PedidoDtos.CambioEstadoRequest;
import com.minimundo.pedidos.dto.PedidoDtos.PedidoDto;
import com.minimundo.pedidos.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contratos internos para ms-logistica y ms-atencion (token de servicio). */
@RestController
@RequestMapping("/api/pedidos/interno")
@PreAuthorize("hasRole('SERVICIO')")
@Tag(name = "Interno", description = "Consulta y cambio de estado del pedido entre microservicios")
public class InternoController {

    private final PedidoService servicio;

    public InternoController(PedidoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pedido completo para logística y atención al cliente")
    public PedidoDto obtener(@PathVariable String id) {
        return servicio.dto(servicio.obtener(id), false);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambio de estado solicitado por ms-logistica (alistamiento, despacho, en ruta, entrega)")
    public PedidoDto estado(@PathVariable String id, @Valid @RequestBody CambioEstadoRequest req) {
        return servicio.cambiarEstado(id, req);
    }
}
