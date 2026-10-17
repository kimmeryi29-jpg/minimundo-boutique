package com.minimundo.catalogo.controller;

import com.minimundo.catalogo.dto.CatalogoDtos.CategoriaDto;
import com.minimundo.catalogo.dto.CatalogoDtos.CategoriaRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.DescuentoRequest;
import com.minimundo.catalogo.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categorías y colecciones", description = "Organización de la oferta por categoría, edad y temporada")
public class CategoriaController {

    private final CategoriaService servicio;

    public CategoriaController(CategoriaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar categorías y colecciones (filtrar por tipo: CATEGORIA, EDAD o TEMPORADA)")
    public List<CategoriaDto> listar(@RequestParam(required = false) String tipo) {
        return servicio.listar(tipo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una categoría o colección")
    public CategoriaDto obtener(@PathVariable String id) {
        return servicio.dto(servicio.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-21 · Crear una categoría o colección")
    @ApiResponse(responseCode = "409", description = "Nombre ya existente")
    public CategoriaDto crear(@Valid @RequestBody CategoriaRequest req) {
        return servicio.crear(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-21 · Editar el nombre (se refleja en los productos asociados)")
    public CategoriaDto actualizar(@PathVariable String id, @Valid @RequestBody CategoriaRequest req) {
        return servicio.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OPERARIO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "HU-21 · Eliminar (se impide si tiene productos asociados)")
    @ApiResponse(responseCode = "409", description = "Tiene productos asociados")
    public void eliminar(@PathVariable String id) {
        servicio.eliminar(id);
    }

    @PutMapping("/{id}/descuento")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-23 · Descuento por categoría con fecha de inicio y fin")
    public CategoriaDto descuento(@PathVariable String id, @Valid @RequestBody DescuentoRequest req) {
        return servicio.ponerDescuento(id, req);
    }

    @DeleteMapping("/{id}/descuento")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "Quitar el descuento de la categoría")
    public CategoriaDto quitarDescuento(@PathVariable String id) {
        return servicio.quitarDescuento(id);
    }
}
