package com.minimundo.pedidos.dto;

import com.minimundo.pedidos.model.CambioEstado;
import com.minimundo.pedidos.model.DireccionEnvio;
import com.minimundo.pedidos.model.ItemPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** DTOs de ms-pedidos. */
public final class PedidoDtos {

    private PedidoDtos() {
    }

    // ---- Carrito

    /** HU-31. */
    public record AgregarItemRequest(@NotBlank(message = "Selecciona la talla y el color") String sku,
                                     @NotNull(message = "Indica la cantidad") @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad) {
    }

    /** HU-32. */
    public record CantidadRequest(@NotNull(message = "Indica la cantidad") @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad) {
    }

    public record LineaCarrito(String sku, String productoId, String nombre, String talla, String color, int cantidad,
                               long precio, long precioFinal, int descuentoPorcentaje, long subtotal, int disponible,
                               boolean disponibleParaVenta, String imagenId) {
    }

    /** HU-34: total = subtotal − descuentos + envío. Sin ciudad, el envío queda pendiente de calcular. */
    public record CarritoDto(List<LineaCarrito> items, int unidades, long subtotal, long descuentos, Long envio,
                             boolean envioPendiente, String ciudad, boolean sinCobertura, long total,
                             List<String> advertencias) {
    }

    // ---- Checkout

    public record DireccionRequest(
            @NotBlank(message = "Indica quién recibe") @Size(max = 100) String nombreRecibe,
            @NotBlank(message = "La dirección es obligatoria") @Size(max = 150) String direccion,
            @Size(max = 80) String barrio,
            @NotBlank(message = "La ciudad es obligatoria") @Size(max = 80) String ciudad,
            @NotBlank(message = "El teléfono es obligatorio") @Pattern(regexp = "[0-9 +()-]{7,20}", message = "El teléfono no es válido") String telefono,
            @Size(max = 200) String indicaciones) {
    }

    /** Pago simulado (HU-39): no se guarda el número completo de la tarjeta. */
    public record PagoRequest(
            @NotBlank(message = "El titular es obligatorio") @Size(max = 100) String titular,
            @NotBlank(message = "El número de tarjeta es obligatorio") String numeroTarjeta,
            @NotBlank(message = "La fecha de vencimiento es obligatoria") @Pattern(regexp = "(0[1-9]|1[0-2])/[0-9]{2}", message = "Usa el formato MM/AA") String vencimiento,
            @NotBlank(message = "El código de seguridad es obligatorio") @Pattern(regexp = "[0-9]{3,4}", message = "El código debe tener 3 o 4 dígitos") String cvv) {
    }

    public record CheckoutRequest(@NotNull(message = "La dirección de envío es obligatoria") @Valid DireccionRequest direccion,
                                  @NotNull(message = "Los datos de pago son obligatorios") @Valid PagoRequest pago) {
    }

    // ---- Pedido

    public record PedidoDto(String id, String codigo, String clienteId, String clienteNombre, String clienteEmail,
                            List<ItemPedido> items, int unidades, long subtotal, long descuentos, long envio, long total,
                            DireccionEnvio direccionEnvio, String estado, String estadoTexto,
                            List<CambioEstado> historialEstados, Instant fechaCreacion, Instant fechaPago,
                            Instant fechaDespacho, Instant fechaEnRuta, Instant fechaEntrega, Instant fechaCancelacion,
                            String transportadora, String guia, String repartidorNombre, boolean puedeCancelar,
                            String tarjetaUltimos4, String referenciaPago) {
    }

    /** Cambio de estado solicitado por ms-logistica (contrato interno). */
    public record CambioEstadoRequest(@NotBlank String estado, String usuarioId, String usuarioNombre, String detalle,
                                      String transportadora, String guia, String repartidorNombre) {
    }

    // ---- Tarifas

    public record TarifaRequest(@NotBlank(message = "La ciudad es obligatoria") @Size(max = 80) String ciudad,
                                @NotNull(message = "El costo es obligatorio") @Min(value = 0, message = "El costo debe ser mayor o igual a cero") Long costo) {
    }

    // ---- Reportes

    public record ConteoEstado(String estado, String texto, long cantidad) {
    }

    public record MasVendido(String productoId, String nombre, long unidades, long total) {
    }

    public record Resumen(String desde, String hasta, long ventasTotal, long numeroVentas, long totalPedidos,
                          List<ConteoEstado> porEstado, List<MasVendido> masVendidos,
                          Double tiempoPromedioDespachoHoras, long pedidosDespachados, boolean sinDatos) {
    }

    public record VentaFila(String codigo, String cliente, Instant fechaPago, String estado, int unidades,
                            long subtotal, long descuentos, long envio, long total) {
    }

    public record ReporteVentas(String desde, String hasta, long total, long numeroPedidos, List<VentaFila> pedidos) {
    }

    public record SinVentas(String productoId, String codigo, String nombre, Instant ultimaVenta, long diasSinVenta) {
    }

    // ---- Contratos externos

    public record PrecioSku(String sku, String productoId, String nombre, String talla, String color, long precio,
                            long precioFinal, int descuentoPorcentaje, boolean disponibleParaVenta, String imagenId) {
    }

    public record PreciosRequest(List<String> skus) {
    }

    public record ItemReserva(String sku, String nombre, String talla, String color, int cantidad) {
    }

    public record ReservaRequest(String pedidoId, String pedidoCodigo, List<ItemReserva> items) {
    }

    public record ProductoActivo(String id, String codigo, String nombre, Instant fechaPublicacion) {
    }
}
