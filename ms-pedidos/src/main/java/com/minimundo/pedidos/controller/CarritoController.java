package com.minimundo.pedidos.controller;

import com.minimundo.pedidos.dto.PedidoDtos.AgregarItemRequest;
import com.minimundo.pedidos.dto.PedidoDtos.CantidadRequest;
import com.minimundo.pedidos.dto.PedidoDtos.CarritoDto;
import com.minimundo.pedidos.security.CurrentUser;
import com.minimundo.pedidos.service.CarritoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carrito")
@PreAuthorize("hasRole('CLIENTE')")
@Tag(name = "Carrito", description = "Carrito de compras del cliente")
public class CarritoController {

    private final CarritoService servicio;

    public CarritoController(CarritoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "HU-34 · Resumen del carrito (subtotal, descuentos, envío según ciudad y total)")
    public CarritoDto ver(@RequestParam(required = false) String ciudad) {
        return servicio.resumen(CurrentUser.get().id(), ciudad);
    }

    @PostMapping("/items")
    @Operation(summary = "HU-31 · Agregar un producto indicando la variante (talla y color)")
    @ApiResponse(responseCode = "409", description = "HU-35 · No hay stock suficiente")
    public CarritoDto agregar(@Valid @RequestBody AgregarItemRequest req,
                              @RequestParam(required = false) String ciudad) {
        return servicio.agregar(CurrentUser.get().id(), req.sku(), req.cantidad(), ciudad);
    }

    @PutMapping("/items/{sku}")
    @Operation(summary = "HU-32 · Modificar la cantidad de una línea")
    public CarritoDto cantidad(@PathVariable String sku, @Valid @RequestBody CantidadRequest req,
                               @RequestParam(required = false) String ciudad) {
        return servicio.cambiarCantidad(CurrentUser.get().id(), sku, req.cantidad(), ciudad);
    }

    @DeleteMapping("/items/{sku}")
    @Operation(summary = "HU-33 · Quitar un producto del carrito")
    public CarritoDto quitar(@PathVariable String sku, @RequestParam(required = false) String ciudad) {
        return servicio.quitar(CurrentUser.get().id(), sku, ciudad);
    }
}
