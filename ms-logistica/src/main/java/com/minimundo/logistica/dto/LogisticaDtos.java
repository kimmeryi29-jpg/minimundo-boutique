package com.minimundo.logistica.dto;

import com.minimundo.logistica.model.DireccionEnvio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** DTOs de ms-logistica. */
public final class LogisticaDtos {

    private LogisticaDtos() {
    }

    // ---- Inventario

    public record InventarioDto(String sku, String productoId, String productoNombre, String talla, String color,
                                int stock, int reservado, int disponible, int stockMinimo, boolean minimoPropio,
                                boolean bajo, boolean agotado, Instant fechaActualizacion) {
    }

    public record InventarioListado(List<InventarioDto> items, int alertas, boolean catalogoDisponible) {
    }

    /** HU-46. */
    public record EntradaRequest(@NotBlank(message = "El SKU es obligatorio") String sku,
                                 @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer cantidad,
                                 @Size(max = 200) String motivo) {
    }

    /** HU-48: el motivo es obligatorio (prenda dañada, conteo físico...). */
    public record AjusteRequest(@NotBlank(message = "El SKU es obligatorio") String sku,
                                @NotNull(message = "La cantidad es obligatoria") Integer cantidad,
                                @NotBlank(message = "Indica el motivo del ajuste") @Size(max = 200) String motivo) {
    }

    public record MinimoRequest(@Min(value = 0, message = "El mínimo debe ser un entero mayor o igual a cero") Integer stockMinimo) {
    }

    /** HU-82. */
    public record ParametrosLogistica(@NotNull(message = "El stock mínimo es obligatorio") @Min(value = 0, message = "Debe ser un entero mayor o igual a cero") Integer stockMinimoDefecto) {
    }

    public record ReingresoRequest(@NotBlank(message = "La solicitud es obligatoria") String solicitudId) {
    }

    // ---- Reservas (contrato interno con ms-pedidos)

    public record ItemReservaRequest(@NotBlank String sku, String nombre, String talla, String color,
                                     @Min(1) int cantidad) {
    }

    public record ReservaRequest(@NotBlank String pedidoId, String pedidoCodigo,
                                 @NotEmpty List<@Valid ItemReservaRequest> items) {
    }

    public record FaltanteStock(String sku, String nombre, String talla, String color, int solicitado,
                                int disponible) {
    }

    public record ReservaResponse(String pedidoId, String estado) {
    }

    // ---- Envíos y entregas

    public record TomarPedidoRequest(@NotBlank(message = "El pedido es obligatorio") String pedidoId) {
    }

    public record VerificacionRequest(@NotBlank(message = "Indica el SKU") String sku,
                                      @NotNull(message = "Indica la cantidad") Integer cantidad) {
    }

    /** HU-53. */
    public record DespachoRequest(@NotBlank(message = "La transportadora es obligatoria") @Size(max = 60) String transportadora,
                                  @NotBlank(message = "El número de guía es obligatorio") @Size(max = 40) String guia) {
    }

    public record EstadoEnvioRequest(@NotBlank String estado, String transportadora, String guia) {
    }

    /** HU-106. */
    public record AsignarRepartidorRequest(@NotBlank(message = "Selecciona un repartidor") String repartidorId) {
    }

    /** HU-109: motivo CLIENTE_AUSENTE, DIRECCION_ERRONEA u OTRO. */
    public record EntregaFallidaRequest(@NotBlank(message = "Selecciona el motivo") String motivo,
                                        @Size(max = 300) String detalle) {
    }

    public record HistorialEntrega(String envioId, String pedidoId, String pedidoCodigo, String clienteNombre,
                                   String direccion, String ciudad, Instant fecha, String resultado, String motivo,
                                   String detalle) {
    }

    // ---- Contratos externos que consume este servicio

    public record VarianteCatalogo(String sku, String productoId, String productoNombre, String codigo,
                                   String talla, String color, boolean activo, boolean publicado) {
    }

    public record ItemPedido(String sku, String nombre, String talla, String color, int cantidad) {
    }

    public record PedidoInfo(String id, String codigo, String clienteId, String clienteNombre, String clienteEmail,
                             String estado, DireccionEnvio direccionEnvio, List<ItemPedido> items, Instant fechaPago) {
    }

    public record CambioEstadoPedido(String estado, String usuarioId, String usuarioNombre, String detalle,
                                     String transportadora, String guia, String repartidorNombre) {
    }

    public record UsuarioInfo(String id, String nombre, String email, String rol, boolean activo) {
    }

    public record SolicitudInfo(String id, String numero, String tipo, String estado, String sku, String nombreProducto,
                                String talla, String color, int cantidad, boolean reingresada, String pedidoCodigo) {
    }

    public record ReingresoMarca(String usuarioNombre) {
    }
}
