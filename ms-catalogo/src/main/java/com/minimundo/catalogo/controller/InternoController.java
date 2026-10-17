package com.minimundo.catalogo.controller;

import com.minimundo.catalogo.dto.CatalogoDtos.PrecioSku;
import com.minimundo.catalogo.dto.CatalogoDtos.PreciosRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoActivo;
import com.minimundo.catalogo.dto.CatalogoDtos.VarianteInfo;
import com.minimundo.catalogo.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints internos: solo los llaman otros microservicios con su token de servicio (no pasan por el gateway). */
@RestController
@RequestMapping("/api/productos/interno")
@PreAuthorize("hasAnyRole('SERVICIO','ADMIN')")
@Tag(name = "Interno", description = "Contratos entre microservicios")
public class InternoController {

    private final ProductoService servicio;

    public InternoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/precios")
    @Operation(summary = "Precios vigentes por SKU (checkout de ms-pedidos)")
    public List<PrecioSku> precios(@Valid @RequestBody PreciosRequest req) {
        return servicio.precios(req.skus());
    }

    @GetMapping("/variantes")
    @Operation(summary = "Todas las variantes del catálogo (inventario de ms-logistica)")
    public List<VarianteInfo> variantes() {
        return servicio.todasLasVariantes();
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Información de una variante por SKU")
    public VarianteInfo sku(@PathVariable String sku) {
        return servicio.variantePorSku(sku);
    }

    @GetMapping("/variante")
    @Operation(summary = "Variante de un producto por talla y color (cambio de talla en ms-atencion)")
    public VarianteInfo variante(@RequestParam String productoId, @RequestParam String talla,
                                 @RequestParam(required = false) String color) {
        return servicio.varianteDe(productoId, talla, color);
    }

    @GetMapping("/activos")
    @Operation(summary = "Productos publicados y activos (indicador de productos sin ventas)")
    public List<ProductoActivo> activos() {
        return servicio.activos();
    }
}
