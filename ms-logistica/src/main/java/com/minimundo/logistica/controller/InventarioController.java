package com.minimundo.logistica.controller;

import com.minimundo.logistica.dto.LogisticaDtos.AjusteRequest;
import com.minimundo.logistica.dto.LogisticaDtos.EntradaRequest;
import com.minimundo.logistica.dto.LogisticaDtos.InventarioDto;
import com.minimundo.logistica.dto.LogisticaDtos.InventarioListado;
import com.minimundo.logistica.dto.LogisticaDtos.MinimoRequest;
import com.minimundo.logistica.dto.LogisticaDtos.ParametrosLogistica;
import com.minimundo.logistica.dto.LogisticaDtos.ReingresoRequest;
import com.minimundo.logistica.model.Movimiento;
import com.minimundo.logistica.security.CurrentUser;
import com.minimundo.logistica.service.InventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventario")
@Tag(name = "Inventario", description = "Stock por SKU, entradas, ajustes, alertas y movimientos")
public class InventarioController {

    private final InventarioService servicio;

    public InventarioController(InventarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN')")
    @Operation(summary = "HU-47/HU-60 · Inventario por SKU, talla y color (filtro=bajo|agotado)")
    public InventarioListado listar(@RequestParam(required = false) String sku,
                                    @RequestParam(required = false) String talla,
                                    @RequestParam(required = false) String color,
                                    @RequestParam(required = false) String filtro) {
        return servicio.listar(sku, talla, color, filtro);
    }

    @GetMapping("/bajo-stock")
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN')")
    @Operation(summary = "HU-49 · Alertas de variantes por debajo del stock mínimo")
    public List<InventarioDto> bajoStock() {
        return servicio.alertas();
    }

    @GetMapping("/disponibilidad")
    @Operation(summary = "HU-29/HU-35 · Unidades disponibles (stock − reservado) por SKU. Público")
    public Map<String, Integer> disponibilidad(@RequestParam String skus) {
        return servicio.disponibilidad(Arrays.stream(skus.split(",")).filter(s -> !s.isBlank()).toList());
    }

    @PostMapping("/entradas")
    @PreAuthorize("hasRole('OPERARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-46 · Registrar una entrada de mercancía")
    @ApiResponse(responseCode = "400", description = "SKU inexistente o cantidad menor o igual a cero")
    public InventarioDto entrada(@Valid @RequestBody EntradaRequest req) {
        return servicio.registrarEntrada(req, CurrentUser.get());
    }

    @PostMapping("/ajustes")
    @PreAuthorize("hasRole('OPERARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-48 · Ajuste manual de stock con motivo")
    @ApiResponse(responseCode = "409", description = "El ajuste dejaría el stock negativo o bajo lo reservado")
    public InventarioDto ajuste(@Valid @RequestBody AjusteRequest req) {
        return servicio.registrarAjuste(req, CurrentUser.get());
    }

    @GetMapping("/{sku}/movimientos")
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN')")
    @Operation(summary = "HU-56 · Historial de movimientos de un SKU (no editables)")
    public List<Movimiento> movimientos(@PathVariable String sku,
                                        @RequestParam(required = false) String tipo,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.movimientos(sku, tipo, desde, hasta);
    }

    @PutMapping("/{sku}/minimo")
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN')")
    @Operation(summary = "Definir el stock mínimo propio de una variante (vacío = usar el mínimo por defecto)")
    public InventarioDto minimo(@PathVariable String sku, @Valid @RequestBody MinimoRequest req) {
        return servicio.definirMinimo(sku, req.stockMinimo());
    }

    @PostMapping("/reingresos")
    @PreAuthorize("hasRole('OPERARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-59 · Reingresar la mercancía de una devolución aprobada")
    public InventarioDto reingreso(@Valid @RequestBody ReingresoRequest req) {
        return servicio.reingresarDevolucion(req.solicitudId(), CurrentUser.get());
    }

    @GetMapping("/parametros")
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN')")
    @Operation(summary = "Consultar el stock mínimo por defecto")
    public ParametrosLogistica parametros() {
        return new ParametrosLogistica(servicio.parametros().getStockMinimoDefecto());
    }

    @PutMapping("/parametros")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-82 · Definir el stock mínimo por defecto")
    public ParametrosLogistica actualizarParametros(@Valid @RequestBody ParametrosLogistica req) {
        return new ParametrosLogistica(servicio.actualizarParametros(req.stockMinimoDefecto()).getStockMinimoDefecto());
    }

    @GetMapping(value = "/exportar", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-86 · Exportar el inventario en CSV")
    public ResponseEntity<byte[]> exportar() {
        byte[] csv = ("﻿" + servicio.csv()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventario.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
