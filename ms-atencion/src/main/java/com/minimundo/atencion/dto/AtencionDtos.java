package com.minimundo.atencion.dto;

import com.minimundo.atencion.model.Historial;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTOs de ms-atencion. */
public final class AtencionDtos {

    private AtencionDtos() {
    }

    /** HU-61 y HU-62. */
    public record RadicarPqrsRequest(@NotBlank(message = "Selecciona el tipo de PQRS") String tipo,
                                     @NotBlank(message = "Describe tu caso") @Size(max = 2000, message = "Máximo 2000 caracteres") String descripcion,
                                     String pedidoId) {
    }

    /** HU-70. */
    public record RespuestaRequest(@NotBlank(message = "Escribe la respuesta antes de cerrar") @Size(max = 2000) String respuesta) {
    }

    public record PqrsDto(String id, String radicado, String tipo, String tipoTexto, String descripcion,
                          String clienteId, String clienteNombre, String clienteEmail, String pedidoId,
                          String pedidoCodigo, String estado, String estadoTexto, String asesorId,
                          String asesorNombre, String respuesta, Instant fechaRadicacion, Instant fechaCierre,
                          boolean vencida, long diasHabiles, List<Historial> historial) {
    }

    /** HU-64 y HU-65. */
    public record SolicitudRequest(@NotBlank(message = "Indica si es cambio o devolución") String tipo,
                                   @NotBlank(message = "Selecciona el pedido") String pedidoId,
                                   @NotBlank(message = "Selecciona el producto") String sku,
                                   @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer cantidad,
                                   String tallaDeseada,
                                   @NotBlank(message = "Indica el motivo") @Size(max = 500) String motivo) {
    }

    /** HU-72 y HU-73: al rechazar, el motivo es obligatorio. */
    public record DecisionRequest(@NotNull(message = "Indica si apruebas o rechazas") Boolean aprobar,
                                  @Size(max = 500) String motivo) {
    }

    public record SolicitudDto(String id, String numero, String tipo, String tipoTexto, String clienteId,
                               String clienteNombre, String pedidoId, String pedidoCodigo, String sku,
                               String productoId, String nombreProducto, String talla, String color, int cantidad,
                               String tallaDeseada, String motivo, String estado, String estadoTexto,
                               String motivoRechazo, String asesorNombre, Instant fechaSolicitud,
                               Instant fechaDecision, boolean reingresada, Instant fechaReingreso,
                               List<Historial> historial) {
    }

    public record ParametrosAtencion(
            @NotNull(message = "El plazo es obligatorio") @Min(value = 1, message = "El plazo debe ser un entero mayor que cero") Integer plazoCambiosDias,
            @NotNull(message = "Los días de vencimiento son obligatorios") @Min(value = 1, message = "Debe ser un entero mayor que cero") Integer diasVencimientoPqrs) {
    }

    public record ReportePqrs(String desde, String hasta, long total, Map<String, Long> porTipo,
                              Map<String, Long> porEstado, long vencidas, boolean sinDatos) {
    }

    public record ReingresoMarca(String usuarioNombre) {
    }

    // ---- Contratos externos

    public record ItemPedido(String sku, String productoId, String nombre, String talla, String color, int cantidad) {
    }

    public record PedidoInfo(String id, String codigo, String clienteId, String clienteNombre, String estado,
                             List<ItemPedido> items, Instant fechaEntrega) {
    }

    public record VarianteInfo(String sku, String productoId, String productoNombre, String codigo, String talla,
                               String color, boolean activo, boolean publicado) {
    }
}
