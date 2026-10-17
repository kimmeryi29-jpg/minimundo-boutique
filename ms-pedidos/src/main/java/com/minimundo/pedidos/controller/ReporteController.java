package com.minimundo.pedidos.controller;

import com.minimundo.pedidos.dto.PedidoDtos.ReporteVentas;
import com.minimundo.pedidos.dto.PedidoDtos.Resumen;
import com.minimundo.pedidos.dto.PedidoDtos.SinVentas;
import com.minimundo.pedidos.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Reportes", description = "Indicadores de ventas y operación para el administrador")
public class ReporteController {

    private final ReporteService servicio;

    public ReporteController(ReporteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-76 a HU-79, HU-88 · Panel: ventas, pedidos por estado, más vendidos y tiempo de despacho")
    public Resumen resumen(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.resumen(desde, hasta);
    }

    @GetMapping("/ventas")
    @PreAuthorize("hasAnyRole('ADMIN','ASESOR','OPERARIO')")
    @Operation(summary = "HU-45 · Reporte de ventas por rango de fechas")
    public ReporteVentas ventas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.ventas(desde, hasta);
    }

    @GetMapping(value = "/ventas/exportar", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-86 · Exportar el reporte de ventas en CSV")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        byte[] csv = ("﻿" + servicio.ventasCsv(desde, hasta)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ventas.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/sin-ventas")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-80 · Productos que llevan tiempo sin venderse")
    public List<SinVentas> sinVentas(@RequestParam(defaultValue = "30") int dias) {
        return servicio.sinVentas(dias);
    }
}
