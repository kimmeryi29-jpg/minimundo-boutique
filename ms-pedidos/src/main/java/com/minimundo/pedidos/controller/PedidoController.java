package com.minimundo.pedidos.controller;

import com.minimundo.pedidos.dto.PedidoDtos.CheckoutRequest;
import com.minimundo.pedidos.dto.PedidoDtos.PedidoDto;
import com.minimundo.pedidos.security.CurrentUser;
import com.minimundo.pedidos.service.PedidoService;
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
@RequestMapping("/api/pedidos")
@Tag(name = "Pedidos", description = "Checkout con pago simulado, mis pedidos y consulta del personal")
public class PedidoController {

    private final PedidoService servicio;

    public PedidoController(PedidoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-39 · Confirmar la compra: precios vigentes, reserva de stock y pago simulado")
    @ApiResponse(responseCode = "402", description = "HU-40 · Pago rechazado (el carrito se conserva)")
    @ApiResponse(responseCode = "409", description = "HU-36 · Alguna variante no tiene stock suficiente")
    @ApiResponse(responseCode = "503", description = "HU-103 · Un servicio no respondió; se liberó la reserva")
    public PedidoDto checkout(@Valid @RequestBody CheckoutRequest req) {
        return servicio.checkout(CurrentUser.get(), req);
    }

    @GetMapping("/mis-pedidos")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "HU-42 · Historial de mis pedidos")
    public List<PedidoDto> misPedidos() {
        return servicio.misPedidos(CurrentUser.get().id());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ASESOR','OPERARIO')")
    @Operation(summary = "Pedidos por estado, cliente o código (cola de alistamiento: estado=PAGADO, HU-50)")
    public List<PedidoDto> listar(@RequestParam(required = false) String estado,
                                  @RequestParam(required = false) String clienteId,
                                  @RequestParam(required = false) String codigo) {
        return servicio.listar(estado, clienteId, codigo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "HU-43/HU-87 · Detalle, estado e historial de estados de un pedido")
    @ApiResponse(responseCode = "403", description = "El pedido no pertenece al cliente")
    public PedidoDto detalle(@PathVariable String id) {
        return servicio.detalle(id, CurrentUser.get());
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
    @Operation(summary = "HU-44 · Cancelar un pedido que aún no ha sido despachado")
    @ApiResponse(responseCode = "409", description = "El pedido ya fue despachado")
    public PedidoDto cancelar(@PathVariable String id) {
        return servicio.cancelar(id, CurrentUser.get());
    }
}
