package com.minimundo.pedidos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Documento Pedido (db_pedidos.pedidos) con su ciclo de vida completo (sección 5.4). */
@Document("pedidos")
public class Pedido {

    @Id
    private String id;
    /** HU-41: código único; dos pedidos nunca comparten el mismo código. */
    @Indexed(unique = true)
    private String codigo;
    @Indexed
    private String clienteId;
    private String clienteNombre;
    private String clienteEmail;
    private List<ItemPedido> items = new ArrayList<>();
    private long subtotal;
    private long descuentos;
    private long envio;
    private long total;
    private DireccionEnvio direccionEnvio;
    @Indexed
    private EstadoPedido estado;
    private List<CambioEstado> historialEstados = new ArrayList<>();
    @Indexed
    private Instant fechaCreacion;
    @Indexed
    private Instant fechaPago;
    private Instant fechaDespacho;
    private Instant fechaEnRuta;
    private Instant fechaEntrega;
    private Instant fechaCancelacion;
    private String transportadora;
    private String guia;
    private String repartidorNombre;
    private String referenciaPago;
    private String tarjetaUltimos4;
    private int intentosPago;

    public void registrar(EstadoPedido nuevo, String usuarioId, String usuarioNombre, String detalle) {
        this.estado = nuevo;
        this.historialEstados.add(new CambioEstado(nuevo, Instant.now(), usuarioId, usuarioNombre, detalle));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }
    public List<ItemPedido> getItems() { return items; }
    public void setItems(List<ItemPedido> items) { this.items = items; }
    public long getSubtotal() { return subtotal; }
    public void setSubtotal(long subtotal) { this.subtotal = subtotal; }
    public long getDescuentos() { return descuentos; }
    public void setDescuentos(long descuentos) { this.descuentos = descuentos; }
    public long getEnvio() { return envio; }
    public void setEnvio(long envio) { this.envio = envio; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public DireccionEnvio getDireccionEnvio() { return direccionEnvio; }
    public void setDireccionEnvio(DireccionEnvio direccionEnvio) { this.direccionEnvio = direccionEnvio; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public List<CambioEstado> getHistorialEstados() { return historialEstados; }
    public void setHistorialEstados(List<CambioEstado> historialEstados) { this.historialEstados = historialEstados; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaPago() { return fechaPago; }
    public void setFechaPago(Instant fechaPago) { this.fechaPago = fechaPago; }
    public Instant getFechaDespacho() { return fechaDespacho; }
    public void setFechaDespacho(Instant fechaDespacho) { this.fechaDespacho = fechaDespacho; }
    public Instant getFechaEnRuta() { return fechaEnRuta; }
    public void setFechaEnRuta(Instant fechaEnRuta) { this.fechaEnRuta = fechaEnRuta; }
    public Instant getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(Instant fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public Instant getFechaCancelacion() { return fechaCancelacion; }
    public void setFechaCancelacion(Instant fechaCancelacion) { this.fechaCancelacion = fechaCancelacion; }
    public String getTransportadora() { return transportadora; }
    public void setTransportadora(String transportadora) { this.transportadora = transportadora; }
    public String getGuia() { return guia; }
    public void setGuia(String guia) { this.guia = guia; }
    public String getRepartidorNombre() { return repartidorNombre; }
    public void setRepartidorNombre(String repartidorNombre) { this.repartidorNombre = repartidorNombre; }
    public String getReferenciaPago() { return referenciaPago; }
    public void setReferenciaPago(String referenciaPago) { this.referenciaPago = referenciaPago; }
    public String getTarjetaUltimos4() { return tarjetaUltimos4; }
    public void setTarjetaUltimos4(String tarjetaUltimos4) { this.tarjetaUltimos4 = tarjetaUltimos4; }
    public int getIntentosPago() { return intentosPago; }
    public void setIntentosPago(int intentosPago) { this.intentosPago = intentosPago; }
}
