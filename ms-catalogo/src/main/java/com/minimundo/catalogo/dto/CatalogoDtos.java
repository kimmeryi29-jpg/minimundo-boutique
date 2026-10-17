package com.minimundo.catalogo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** DTOs del catálogo. */
public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    public record VarianteRequest(
            @NotBlank(message = "La talla es obligatoria") @Size(max = 20) String talla,
            @NotBlank(message = "El color es obligatorio") @Size(max = 30) String color) {
    }

    /** HU-16, HU-19, HU-24: nombre, precio y al menos una variante son obligatorios. */
    public record ProductoRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120, message = "Máximo 120 caracteres") String nombre,
            @Size(max = 2000, message = "Máximo 2000 caracteres") String descripcion,
            @NotBlank(message = "La categoría es obligatoria") String categoriaId,
            List<String> coleccionIds,
            @NotBlank(message = "El género es obligatorio") String genero,
            @NotBlank(message = "El rango de edad es obligatorio") String rangoEdad,
            @NotNull(message = "El precio es obligatorio") @Positive(message = "El precio debe ser mayor que cero") Long precio,
            @NotEmpty(message = "Agrega al menos una variante de talla y color") List<@Valid VarianteRequest> variantes,
            boolean publicar) {
    }

    /** HU-23. */
    public record DescuentoRequest(
            @NotNull(message = "El porcentaje es obligatorio") @Min(value = 1, message = "El porcentaje debe estar entre 1 y 100") @Max(value = 100, message = "El porcentaje debe estar entre 1 y 100") Integer porcentaje,
            @NotNull(message = "La fecha de inicio es obligatoria") LocalDate inicio,
            @NotNull(message = "La fecha de fin es obligatoria") LocalDate fin) {
    }

    /** HU-21. */
    public record CategoriaRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 60, message = "Máximo 60 caracteres") String nombre,
            @NotBlank(message = "El tipo es obligatorio") String tipo,
            @Size(max = 300) String descripcion) {
    }

    public record DescuentoDto(int porcentaje, LocalDate inicio, LocalDate fin, boolean vigente) {
    }

    public record CategoriaDto(String id, String nombre, String tipo, String tipoTexto, String descripcion,
                               DescuentoDto descuento, long productos) {
    }

    public record ColeccionRef(String id, String nombre, String tipo) {
    }

    public record VarianteDto(String sku, String talla, String color) {
    }

    public record ProductoDto(String id, String codigo, String nombre, String descripcion,
                              String categoriaId, String categoriaNombre, List<ColeccionRef> colecciones,
                              String genero, String generoTexto, String rangoEdad, String rangoEdadTexto,
                              long precio, long precioFinal, int descuentoPorcentaje, DescuentoDto descuento,
                              String estado, boolean activo, List<String> imagenes, List<VarianteDto> variantes,
                              List<String> tallas, List<String> colores, Instant fechaCreacion,
                              Instant fechaActualizacion) {
    }

    /** Resumen para listados del catálogo público. */
    public record ProductoResumen(String id, String codigo, String nombre, String categoriaNombre, String genero,
                                  String generoTexto, String rangoEdad, String rangoEdadTexto, long precio,
                                  long precioFinal, int descuentoPorcentaje, String imagen, List<String> tallas,
                                  List<String> colores, String estado, boolean activo, int variantes) {
    }

    // ---- Contratos internos (consumidos por ms-pedidos, ms-logistica y ms-atencion) ----

    public record PreciosRequest(@NotEmpty List<String> skus) {
    }

    public record PrecioSku(String sku, String productoId, String nombre, String talla, String color, long precio,
                            long precioFinal, int descuentoPorcentaje, boolean disponibleParaVenta, String imagenId) {
    }

    public record VarianteInfo(String sku, String productoId, String productoNombre, String codigo, String talla,
                               String color, boolean activo, boolean publicado) {
    }

    public record ProductoActivo(String id, String codigo, String nombre, Instant fechaPublicacion) {
    }

    public record Opcion(String valor, String texto) {
    }

    public record OpcionesCatalogo(List<Opcion> generos, List<Opcion> rangosEdad, List<Opcion> tiposCategoria,
                                   List<String> tallasSugeridas, List<String> coloresSugeridos) {
    }
}
