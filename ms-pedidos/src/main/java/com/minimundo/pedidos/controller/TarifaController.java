package com.minimundo.pedidos.controller;

import com.minimundo.pedidos.dto.PedidoDtos.TarifaRequest;
import com.minimundo.pedidos.model.TarifaEnvio;
import com.minimundo.pedidos.service.TarifaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tarifas-envio")
@Tag(name = "Tarifas de envío", description = "Costo de envío por ciudad")
public class TarifaController {

    private final TarifaService servicio;

    public TarifaController(TarifaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "HU-38 · Ciudades con cobertura y su costo de envío (público)")
    public List<TarifaEnvio> listar() {
        return servicio.listar();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-81 · Definir el costo de envío de una ciudad")
    public TarifaEnvio crear(@Valid @RequestBody TarifaRequest req) {
        return servicio.crear(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-81 · Cambiar el costo de envío (aplica de inmediato en el checkout)")
    public TarifaEnvio actualizar(@PathVariable String id, @Valid @RequestBody TarifaRequest req) {
        return servicio.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar la cobertura de una ciudad")
    public void eliminar(@PathVariable String id) {
        servicio.eliminar(id);
    }
}
