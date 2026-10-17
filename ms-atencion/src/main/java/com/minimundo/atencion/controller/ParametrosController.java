package com.minimundo.atencion.controller;

import com.minimundo.atencion.dto.AtencionDtos.ParametrosAtencion;
import com.minimundo.atencion.model.Parametros;
import com.minimundo.atencion.service.ParametrosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/atencion/parametros")
@Tag(name = "Parámetros de atención", description = "Plazo de cambios y vencimiento de PQRS")
public class ParametrosController {

    private final ParametrosService servicio;

    public ParametrosController(ParametrosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Consultar el plazo de cambios y devoluciones y los días de vencimiento de PQRS")
    public ParametrosAtencion obtener() {
        Parametros p = servicio.obtener();
        return new ParametrosAtencion(p.getPlazoCambiosDias(), p.getDiasVencimientoPqrs());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-83/HU-84 · Actualizar los parámetros de atención")
    public ParametrosAtencion actualizar(@Valid @RequestBody ParametrosAtencion req) {
        Parametros p = servicio.actualizar(req.plazoCambiosDias(), req.diasVencimientoPqrs());
        return new ParametrosAtencion(p.getPlazoCambiosDias(), p.getDiasVencimientoPqrs());
    }
}
