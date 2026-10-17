package com.minimundo.catalogo.controller;

import com.minimundo.catalogo.dto.CatalogoDtos.DescuentoRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.Opcion;
import com.minimundo.catalogo.dto.CatalogoDtos.OpcionesCatalogo;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoDto;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoRequest;
import com.minimundo.catalogo.dto.CatalogoDtos.ProductoResumen;
import com.minimundo.catalogo.dto.CatalogoDtos.VarianteRequest;
import com.minimundo.catalogo.model.Genero;
import com.minimundo.catalogo.model.RangoEdad;
import com.minimundo.catalogo.model.TipoCategoria;
import com.minimundo.catalogo.security.CurrentUser;
import com.minimundo.catalogo.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
@Tag(name = "Productos", description = "Catálogo público y gestión de productos del operario")
public class ProductoController {

    private final ProductoService servicio;

    public ProductoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "HU-27/HU-28 · Catálogo público con filtros y búsqueda por nombre")
    public List<ProductoResumen> catalogo(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String categoria,
                                          @RequestParam(required = false) String coleccion,
                                          @RequestParam(required = false) String edad,
                                          @RequestParam(required = false) String talla,
                                          @RequestParam(required = false) String genero,
                                          @RequestParam(required = false) Long precioMin,
                                          @RequestParam(required = false) Long precioMax,
                                          @RequestParam(required = false) String orden) {
        return servicio.buscarPublico(q, categoria, coleccion, edad, talla, genero, precioMin, precioMax, orden);
    }

    @GetMapping("/opciones")
    @Operation(summary = "Valores permitidos para géneros, rangos de edad, tipos de colección y tallas sugeridas")
    public OpcionesCatalogo opciones() {
        return new OpcionesCatalogo(
                Arrays.stream(Genero.values()).map(g -> new Opcion(g.name(), g.getTexto())).toList(),
                Arrays.stream(RangoEdad.values()).map(r -> new Opcion(r.name(), r.getTexto())).toList(),
                Arrays.stream(TipoCategoria.values()).map(t -> new Opcion(t.name(), t.getTexto())).toList(),
                List.of("0-3M", "3-6M", "6-9M", "9-12M", "12-18M", "18-24M", "2", "4", "6", "8", "10", "12",
                        "18", "20", "22", "24", "26", "28", "30", "32", "34", "UNICA"),
                List.of("Blanco", "Negro", "Rosa", "Azul", "Celeste", "Lila", "Amarillo", "Verde", "Rojo", "Beige",
                        "Gris", "Café", "Multicolor"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "HU-29 · Detalle de un producto con tallas, colores e imágenes")
    @ApiResponse(responseCode = "404", description = "Producto no disponible (desactivado o en borrador)")
    public ProductoDto detalle(@PathVariable String id) {
        boolean gestion = CurrentUser.opcional().map(u -> u.tieneRol("OPERARIO", "ADMIN", "ASESOR", "SERVICIO"))
                .orElse(false);
        return servicio.detallePublico(id, gestion);
    }

    @GetMapping("/gestion")
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN')")
    @Operation(summary = "Listado de gestión (incluye borradores y desactivados)")
    public List<ProductoResumen> gestion(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String estado,
                                         @RequestParam(required = false) Boolean activo) {
        return servicio.listarGestion(q, estado, activo);
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-16 · Crear un producto con sus variantes (borrador o publicado)")
    @ApiResponse(responseCode = "400", description = "Falta nombre, precio o al menos una variante (HU-24)")
    @ApiResponse(responseCode = "409", description = "SKU repetido (HU-25)")
    public ProductoDto crear(@Valid @RequestBody ProductoRequest req) {
        return servicio.crear(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-19 · Editar la información de un producto")
    public ProductoDto actualizar(@PathVariable String id, @Valid @RequestBody ProductoRequest req) {
        return servicio.actualizar(id, req);
    }

    @PatchMapping("/{id}/publicar")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-22 · Publicar un borrador completo")
    public ProductoDto publicar(@PathVariable String id) {
        return servicio.publicar(id);
    }

    @PatchMapping("/{id}/borrador")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-22 · Devolver un producto a borrador")
    public ProductoDto borrador(@PathVariable String id) {
        return servicio.volverABorrador(id);
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-20 · Desactivar un producto que ya no se vende")
    public ProductoDto desactivar(@PathVariable String id) {
        return servicio.cambiarActivo(id, false);
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "Reactivar un producto")
    public ProductoDto activar(@PathVariable String id) {
        return servicio.cambiarActivo(id, true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-20 · Eliminación lógica: desactiva y conserva el historial")
    public ProductoDto eliminar(@PathVariable String id) {
        return servicio.cambiarActivo(id, false);
    }

    @PostMapping("/{id}/variantes")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-17 · Agregar una variante de talla y color")
    public ProductoDto agregarVariante(@PathVariable String id, @Valid @RequestBody VarianteRequest req) {
        return servicio.agregarVariante(id, req);
    }

    @DeleteMapping("/{id}/variantes/{sku}")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "Quitar una variante (el producto conserva al menos una)")
    public ProductoDto eliminarVariante(@PathVariable String id, @PathVariable String sku) {
        return servicio.eliminarVariante(id, sku);
    }

    @PostMapping(value = "/{id}/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-18 · Cargar varias imágenes JPG o PNG")
    @ApiResponse(responseCode = "400", description = "Formato no permitido o tamaño superior al máximo")
    public ProductoDto imagenes(@PathVariable String id, @RequestPart("archivos") List<MultipartFile> archivos) {
        return servicio.agregarImagenes(id, archivos);
    }

    @DeleteMapping("/{id}/imagenes/{imagenId}")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "Quitar una imagen del producto")
    public ProductoDto quitarImagen(@PathVariable String id, @PathVariable String imagenId) {
        return servicio.eliminarImagen(id, imagenId);
    }

    @PutMapping("/{id}/descuento")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "HU-23 · Aplicar un descuento con fecha de inicio y fin")
    public ProductoDto descuento(@PathVariable String id, @Valid @RequestBody DescuentoRequest req) {
        return servicio.ponerDescuento(id, req);
    }

    @DeleteMapping("/{id}/descuento")
    @PreAuthorize("hasRole('OPERARIO')")
    @Operation(summary = "Quitar el descuento del producto")
    public ProductoDto quitarDescuento(@PathVariable String id) {
        return servicio.quitarDescuento(id);
    }
}
