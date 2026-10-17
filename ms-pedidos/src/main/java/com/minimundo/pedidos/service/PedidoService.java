package com.minimundo.pedidos.service;

import com.minimundo.pedidos.dto.PedidoDtos.CambioEstadoRequest;
import com.minimundo.pedidos.dto.PedidoDtos.CheckoutRequest;
import com.minimundo.pedidos.dto.PedidoDtos.DireccionRequest;
import com.minimundo.pedidos.dto.PedidoDtos.ItemReserva;
import com.minimundo.pedidos.dto.PedidoDtos.PedidoDto;
import com.minimundo.pedidos.dto.PedidoDtos.PrecioSku;
import com.minimundo.pedidos.dto.PedidoDtos.ReservaRequest;
import com.minimundo.pedidos.error.ApiException;
import com.minimundo.pedidos.model.Carrito;
import com.minimundo.pedidos.model.DireccionEnvio;
import com.minimundo.pedidos.model.EstadoPedido;
import com.minimundo.pedidos.model.ItemCarrito;
import com.minimundo.pedidos.model.ItemPedido;
import com.minimundo.pedidos.model.Pedido;
import com.minimundo.pedidos.model.TarifaEnvio;
import com.minimundo.pedidos.repository.PedidoRepository;
import com.minimundo.pedidos.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Checkout con pago simulado y patrón Saga orquestado simplificado (sección 5.6): (1) precios vigentes en
 * ms-catalogo, (2) reserva de stock en ms-logistica, (3) pago simulado y pedido PAGADO, (4) si algo falla,
 * acción compensatoria que libera la reserva. Incluye consulta de pedidos (HU-42, HU-43, HU-111),
 * cancelación (HU-44) y los cambios de estado que solicita ms-logistica.
 */
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PedidoRepository repo;
    private final MongoTemplate mongo;
    private final CarritoService carritos;
    private final TarifaService tarifas;
    private final Integraciones integraciones;
    private final PagoSimulado pagos;
    private final SecureRandom random = new SecureRandom();

    public PedidoService(PedidoRepository repo, MongoTemplate mongo, CarritoService carritos, TarifaService tarifas,
                         Integraciones integraciones, PagoSimulado pagos) {
        this.repo = repo;
        this.mongo = mongo;
        this.carritos = carritos;
        this.tarifas = tarifas;
        this.integraciones = integraciones;
        this.pagos = pagos;
    }

    public Pedido obtener(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("El pedido no existe"));
    }

    // ------------------------------------------------------------------ Checkout

    public PedidoDto checkout(AuthUser cliente, CheckoutRequest req) {
        Carrito carrito = carritos.buscar(cliente.id()).filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> ApiException.badRequest("Tu carrito está vacío"));
        DireccionRequest d = req.direccion();
        TarifaEnvio tarifa = tarifas.porCiudad(d.ciudad()).orElseThrow(() -> ApiException.badRequest(
                "No hay cobertura de envío para " + d.ciudad().trim() + ". Elige otra ciudad.",
                Map.of("direccion.ciudad", "Sin cobertura de envío")));
        pagos.validar(req.pago());

        // (1) Precios vigentes en ms-catalogo
        List<String> skus = carrito.getItems().stream().map(ItemCarrito::getSku).toList();
        Map<String, PrecioSku> precios = integraciones.precios(skus);
        List<ItemPedido> items = new ArrayList<>();
        long subtotal = 0;
        long descuentos = 0;
        for (ItemCarrito i : carrito.getItems()) {
            PrecioSku p = precios.get(i.getSku());
            if (p == null || !p.disponibleParaVenta()) {
                throw ApiException.conflict(i.getNombre() + " ya no está disponible. Quítalo de tu carrito para continuar.");
            }
            ItemPedido ip = new ItemPedido();
            ip.setSku(p.sku());
            ip.setProductoId(p.productoId());
            ip.setNombre(p.nombre());
            ip.setTalla(p.talla());
            ip.setColor(p.color());
            ip.setCantidad(i.getCantidad());
            ip.setPrecioOriginal(p.precio());
            ip.setPrecioUnitario(p.precioFinal());
            ip.setDescuentoPorcentaje(p.descuentoPorcentaje());
            ip.setImagenId(p.imagenId());
            items.add(ip);
            subtotal += p.precio() * i.getCantidad();
            descuentos += (p.precio() - p.precioFinal()) * i.getCantidad();
        }

        // Pedido en estado CREADO (se reutiliza el de un pago rechazado anterior: HU-40)
        Pedido pedido = repo.findFirstByClienteIdAndEstadoOrderByFechaCreacionDesc(cliente.id(), EstadoPedido.CREADO)
                .orElse(null);
        boolean nuevo = pedido == null;
        if (nuevo) {
            pedido = new Pedido();
            pedido.setClienteId(cliente.id());
            pedido.setFechaCreacion(Instant.now());
            pedido.registrar(EstadoPedido.CREADO, cliente.id(), cliente.nombre(), "Pedido creado");
        }
        pedido.setClienteNombre(cliente.nombre());
        pedido.setClienteEmail(cliente.email());
        pedido.setItems(items);
        pedido.setSubtotal(subtotal);
        pedido.setDescuentos(descuentos);
        pedido.setEnvio(tarifa.getCosto());
        pedido.setTotal(subtotal - descuentos + tarifa.getCosto());
        pedido.setDireccionEnvio(direccion(d, tarifa.getCiudad()));
        pedido = guardarConCodigo(pedido);

        // (2) Reserva de stock en ms-logistica (todo o nada)
        try {
            integraciones.reservar(new ReservaRequest(pedido.getId(), pedido.getCodigo(), items.stream()
                    .map(i -> new ItemReserva(i.getSku(), i.getNombre(), i.getTalla(), i.getColor(), i.getCantidad()))
                    .toList()));
        } catch (ApiException e) {
            if (nuevo) {
                repo.delete(pedido);
            }
            if (e.getStatus() == HttpStatus.CONFLICT) {
                // HU-36: el pedido no se crea, el carrito se conserva y se indica qué talla y color falta
                throw ApiException.conflict(e.getMessage() + ". Ajusta tu carrito y vuelve a confirmar.");
            }
            throw e;
        }

        // (3) Pago simulado
        try {
            PagoSimulado.Resultado resultado = pagos.procesar(req.pago());
            pedido.setIntentosPago(pedido.getIntentosPago() + 1);
            pedido.setTarjetaUltimos4(resultado.ultimos4());
            if (!resultado.aprobado()) {
                // (4) Compensación: se libera la reserva y el carrito se conserva (HU-40, HU-55)
                liberarSilencioso(pedido.getId());
                pedido.getHistorialEstados().add(new com.minimundo.pedidos.model.CambioEstado(EstadoPedido.CREADO,
                        Instant.now(), cliente.id(), cliente.nombre(), "Pago rechazado: " + resultado.motivo()));
                repo.save(pedido);
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Pago rechazado: " + resultado.motivo()
                        + ". Tu carrito se conservó; puedes intentarlo de nuevo.");
            }
            pedido.setReferenciaPago(resultado.referencia());
            pedido.setFechaPago(Instant.now());
            pedido.registrar(EstadoPedido.PAGADO, cliente.id(), cliente.nombre(),
                    "Pago aprobado · tarjeta terminada en " + resultado.ultimos4());
            Pedido pagado = repo.save(pedido);
            carritos.vaciar(cliente.id());
            return dto(pagado, true);
        } catch (ApiException e) {
            if (e.getStatus() != HttpStatus.PAYMENT_REQUIRED) {
                liberarSilencioso(pedido.getId());
            }
            throw e;
        } catch (RuntimeException e) {
            // HU-103: ante una falla inesperada se ejecuta la acción compensatoria
            log.error("Falla durante el checkout del pedido {}", pedido.getCodigo(), e);
            liberarSilencioso(pedido.getId());
            throw ApiException.unavailable("No pudimos completar tu compra. No se realizó ningún cobro; intenta de nuevo.");
        }
    }

    private void liberarSilencioso(String pedidoId) {
        try {
            integraciones.liberar(pedidoId);
        } catch (RuntimeException e) {
            log.error("No se pudo liberar la reserva del pedido {}", pedidoId, e);
        }
    }

    private DireccionEnvio direccion(DireccionRequest d, String ciudad) {
        DireccionEnvio de = new DireccionEnvio();
        de.setNombreRecibe(d.nombreRecibe().trim());
        de.setDireccion(d.direccion().trim());
        de.setBarrio(d.barrio());
        de.setCiudad(ciudad);
        de.setTelefono(d.telefono().trim());
        de.setIndicaciones(d.indicaciones());
        return de;
    }

    /** HU-41: código legible y único, p. ej. MM-261016-K7Q4X. */
    private Pedido guardarConCodigo(Pedido p) {
        if (p.getCodigo() != null) {
            return repo.save(p);
        }
        String fecha = LocalDate.now(ZONA).format(DateTimeFormatter.ofPattern("yyMMdd"));
        for (int intento = 0; intento < 5; intento++) {
            StringBuilder sb = new StringBuilder("MM-").append(fecha).append('-');
            for (int i = 0; i < 5; i++) {
                sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
            }
            p.setCodigo(sb.toString());
            try {
                return repo.save(p);
            } catch (DuplicateKeyException e) {
                p.setCodigo(null);
            }
        }
        throw ApiException.unavailable("No fue posible generar el código del pedido. Intenta de nuevo.");
    }

    // ------------------------------------------------------------------ Consultas

    /** HU-42: solo sus pedidos, del más reciente al más antiguo. */
    public List<PedidoDto> misPedidos(String clienteId) {
        return repo.findByClienteIdAndEstadoNotOrderByFechaCreacionDesc(clienteId, EstadoPedido.CREADO).stream()
                .map(p -> dto(p, true)).toList();
    }

    /** HU-43 y HU-111. El cliente solo ve sus pedidos; el personal interno ve cualquiera. */
    public PedidoDto detalle(String id, AuthUser usuario) {
        Pedido p = obtener(id);
        boolean esCliente = usuario.tieneRol("CLIENTE");
        if (esCliente && !p.getClienteId().equals(usuario.id())) {
            throw ApiException.forbidden("Este pedido no te pertenece");
        }
        if (!esCliente && !usuario.tieneRol("ADMIN", "ASESOR", "OPERARIO")) {
            throw ApiException.forbidden("No tienes permiso para consultar este pedido");
        }
        return dto(p, esCliente);
    }

    /** Listado del personal (tabla 11): por estado, cliente o código. La cola PAGADO va en orden de pago (HU-50). */
    public List<PedidoDto> listar(String estado, String clienteId, String codigo) {
        Query q = new Query();
        EstadoPedido e = null;
        if (estado != null && !estado.isBlank()) {
            e = parseEstado(estado);
            q.addCriteria(Criteria.where("estado").is(e));
        } else {
            q.addCriteria(Criteria.where("estado").ne(EstadoPedido.CREADO));
        }
        if (clienteId != null && !clienteId.isBlank()) {
            q.addCriteria(Criteria.where("clienteId").is(clienteId));
        }
        if (codigo != null && !codigo.isBlank()) {
            q.addCriteria(Criteria.where("codigo").regex(java.util.regex.Pattern.quote(codigo.trim().toUpperCase(Locale.ROOT)), "i"));
        }
        if (e == EstadoPedido.PAGADO) {
            q.with(Sort.by(Sort.Direction.ASC, "fechaPago"));
        } else {
            q.with(Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        }
        q.limit(500);
        return mongo.find(q, Pedido.class).stream().map(p -> dto(p, false)).toList();
    }

    // ------------------------------------------------------------------ Cancelación (HU-44)

    public PedidoDto cancelar(String id, AuthUser usuario) {
        Pedido p = obtener(id);
        if (usuario.tieneRol("CLIENTE") && !p.getClienteId().equals(usuario.id())) {
            throw ApiException.forbidden("Este pedido no te pertenece");
        }
        if (!p.getEstado().cancelable()) {
            throw ApiException.conflict("No se puede cancelar: el pedido está " + p.getEstado().getTexto().toLowerCase()
                    + ". Si necesitas ayuda, radica una PQRS.");
        }
        integraciones.liberar(p.getId());
        p.setFechaCancelacion(Instant.now());
        p.registrar(EstadoPedido.CANCELADO, usuario.id(), usuario.nombre(), "Cancelado por " + usuario.nombre());
        return dto(repo.save(p), usuario.tieneRol("CLIENTE"));
    }

    // ------------------------------------------------------------------ Cambios solicitados por ms-logistica

    public PedidoDto cambiarEstado(String id, CambioEstadoRequest req) {
        Pedido p = obtener(id);
        EstadoPedido destino = parseEstado(req.estado());
        if (!p.getEstado().puedePasarA(destino)) {
            throw ApiException.conflict("El pedido " + p.getCodigo() + " no puede pasar de " + p.getEstado()
                    + " a " + destino);
        }
        Instant ahora = Instant.now();
        switch (destino) {
            case DESPACHADO -> {
                if (p.getEstado() == EstadoPedido.EN_ALISTAMIENTO) {
                    p.setFechaDespacho(ahora);
                    p.setTransportadora(req.transportadora());
                    p.setGuia(req.guia());
                } else {
                    // Entrega fallida: vuelve a DESPACHADO y se oculta la información de reparto (HU-109, HU-111)
                    p.setRepartidorNombre(null);
                    p.setFechaEnRuta(null);
                }
            }
            case EN_RUTA -> {
                p.setFechaEnRuta(ahora);
                p.setRepartidorNombre(req.repartidorNombre() != null ? req.repartidorNombre() : req.usuarioNombre());
            }
            case ENTREGADO -> p.setFechaEntrega(ahora);
            case CANCELADO -> p.setFechaCancelacion(ahora);
            default -> {
            }
        }
        p.registrar(destino, req.usuarioId(), req.usuarioNombre(), req.detalle());
        return dto(repo.save(p), false);
    }

    // ------------------------------------------------------------------ DTO

    public PedidoDto dto(Pedido p, boolean vistaCliente) {
        boolean infoReparto = p.getEstado() == EstadoPedido.EN_RUTA || p.getEstado() == EstadoPedido.ENTREGADO;
        int unidades = p.getItems().stream().mapToInt(ItemPedido::getCantidad).sum();
        return new PedidoDto(p.getId(), p.getCodigo(), p.getClienteId(), p.getClienteNombre(), p.getClienteEmail(),
                p.getItems(), unidades, p.getSubtotal(), p.getDescuentos(), p.getEnvio(), p.getTotal(),
                p.getDireccionEnvio(), p.getEstado().name(), p.getEstado().getTexto(), p.getHistorialEstados(),
                p.getFechaCreacion(), p.getFechaPago(), p.getFechaDespacho(),
                infoReparto ? p.getFechaEnRuta() : null, p.getFechaEntrega(), p.getFechaCancelacion(),
                p.getTransportadora(), p.getGuia(),
                infoReparto || !vistaCliente ? p.getRepartidorNombre() : null,
                p.getEstado().cancelable() && p.getEstado() != EstadoPedido.CREADO,
                p.getTarjetaUltimos4(), p.getReferenciaPago());
    }

    static EstadoPedido parseEstado(String estado) {
        try {
            return EstadoPedido.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Estado no válido: " + estado);
        }
    }
}
