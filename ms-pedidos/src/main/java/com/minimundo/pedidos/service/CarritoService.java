package com.minimundo.pedidos.service;

import com.minimundo.pedidos.dto.PedidoDtos.CarritoDto;
import com.minimundo.pedidos.dto.PedidoDtos.LineaCarrito;
import com.minimundo.pedidos.dto.PedidoDtos.PrecioSku;
import com.minimundo.pedidos.error.ApiException;
import com.minimundo.pedidos.model.Carrito;
import com.minimundo.pedidos.model.ItemCarrito;
import com.minimundo.pedidos.model.TarifaEnvio;
import com.minimundo.pedidos.repository.CarritoRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Carrito: agregar con talla y color (HU-31), validar stock (HU-35), modificar cantidades (HU-32), quitar (HU-33)
 * y resumen con subtotal, descuentos, envío y total (HU-34, HU-38).
 */
@Service
public class CarritoService {

    private final CarritoRepository repo;
    private final Integraciones integraciones;
    private final TarifaService tarifas;

    public CarritoService(CarritoRepository repo, Integraciones integraciones, TarifaService tarifas) {
        this.repo = repo;
        this.integraciones = integraciones;
        this.tarifas = tarifas;
    }

    public Carrito obtenerOCrear(String clienteId) {
        return repo.findByClienteId(clienteId).orElseGet(() -> {
            Carrito c = new Carrito();
            c.setClienteId(clienteId);
            return c;
        });
    }

    public Optional<Carrito> buscar(String clienteId) {
        return repo.findByClienteId(clienteId);
    }

    /** HU-31: si el SKU ya está en el carrito se suma a la cantidad existente. HU-35: nunca más que el disponible. */
    public CarritoDto agregar(String clienteId, String skuIngresado, int cantidad, String ciudad) {
        if (cantidad < 1) {
            throw ApiException.badRequest("La cantidad debe ser al menos 1");
        }
        String sku = skuIngresado.trim().toUpperCase(Locale.ROOT);
        PrecioSku precio = integraciones.precios(List.of(sku)).get(sku);
        if (precio == null) {
            throw ApiException.notFound("El producto seleccionado no existe");
        }
        if (!precio.disponibleParaVenta()) {
            throw ApiException.conflict("Este producto ya no está disponible");
        }
        Carrito carrito = obtenerOCrear(clienteId);
        ItemCarrito existente = carrito.getItems().stream().filter(i -> i.getSku().equals(sku)).findFirst().orElse(null);
        int nueva = cantidad + (existente == null ? 0 : existente.getCantidad());
        int disponible = integraciones.disponibilidad(List.of(sku)).getOrDefault(sku, 0);
        if (disponible <= 0) {
            throw ApiException.conflict("Agotado: la talla " + precio.talla() + " color " + precio.color()
                    + " no tiene unidades disponibles");
        }
        if (nueva > disponible) {
            throw ApiException.conflict("Solo hay " + disponible + " unidad(es) disponible(s) de talla " + precio.talla()
                    + " color " + precio.color() + (existente == null ? "" : " (ya tienes " + existente.getCantidad()
                    + " en tu carrito)"));
        }
        if (existente == null) {
            ItemCarrito item = new ItemCarrito();
            item.setSku(sku);
            item.setProductoId(precio.productoId());
            item.setNombre(precio.nombre());
            item.setTalla(precio.talla());
            item.setColor(precio.color());
            item.setImagenId(precio.imagenId());
            item.setCantidad(nueva);
            carrito.getItems().add(item);
        } else {
            existente.setCantidad(nueva);
        }
        carrito.setFechaActualizacion(Instant.now());
        repo.save(carrito);
        return resumen(clienteId, ciudad);
    }

    /** HU-32: una cantidad menor a 1 se rechaza y una mayor al stock no se actualiza. */
    public CarritoDto cambiarCantidad(String clienteId, String skuIngresado, int cantidad, String ciudad) {
        if (cantidad < 1) {
            throw ApiException.badRequest("La cantidad debe ser al menos 1");
        }
        String sku = skuIngresado.trim().toUpperCase(Locale.ROOT);
        Carrito carrito = repo.findByClienteId(clienteId)
                .orElseThrow(() -> ApiException.notFound("Tu carrito está vacío"));
        ItemCarrito item = carrito.getItems().stream().filter(i -> i.getSku().equals(sku)).findFirst()
                .orElseThrow(() -> ApiException.notFound("El producto no está en tu carrito"));
        int disponible = integraciones.disponibilidad(List.of(sku)).getOrDefault(sku, 0);
        if (cantidad > disponible) {
            throw ApiException.conflict("Solo hay " + disponible + " unidad(es) disponible(s) de " + item.getNombre()
                    + " talla " + item.getTalla() + " color " + item.getColor());
        }
        item.setCantidad(cantidad);
        carrito.setFechaActualizacion(Instant.now());
        repo.save(carrito);
        return resumen(clienteId, ciudad);
    }

    /** HU-33. */
    public CarritoDto quitar(String clienteId, String skuIngresado, String ciudad) {
        String sku = skuIngresado.trim().toUpperCase(Locale.ROOT);
        repo.findByClienteId(clienteId).ifPresent(c -> {
            c.getItems().removeIf(i -> i.getSku().equals(sku));
            c.setFechaActualizacion(Instant.now());
            repo.save(c);
        });
        return resumen(clienteId, ciudad);
    }

    public void vaciar(String clienteId) {
        repo.findByClienteId(clienteId).ifPresent(c -> {
            c.getItems().clear();
            c.setFechaActualizacion(Instant.now());
            repo.save(c);
        });
    }

    /** HU-34 y HU-38: precios vigentes del catálogo y costo de envío de la ciudad, si se indicó. */
    public CarritoDto resumen(String clienteId, String ciudad) {
        Carrito carrito = repo.findByClienteId(clienteId).orElse(null);
        if (carrito == null || carrito.getItems().isEmpty()) {
            return new CarritoDto(List.of(), 0, 0, 0, null, true, ciudad, false, 0, List.of());
        }
        List<String> skus = carrito.getItems().stream().map(ItemCarrito::getSku).toList();
        Map<String, PrecioSku> precios = integraciones.precios(skus);
        Map<String, Integer> disponibles = integraciones.disponibilidad(skus);
        List<LineaCarrito> lineas = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        long subtotal = 0;
        long descuentos = 0;
        int unidades = 0;
        for (ItemCarrito i : carrito.getItems()) {
            PrecioSku p = precios.get(i.getSku());
            int disp = disponibles.getOrDefault(i.getSku(), 0);
            boolean vendible = p != null && p.disponibleParaVenta();
            long precio = p == null ? 0 : p.precio();
            long precioFinal = p == null ? 0 : p.precioFinal();
            if (!vendible) {
                advertencias.add(i.getNombre() + " ya no está disponible; quítalo para continuar.");
            } else if (i.getCantidad() > disp) {
                advertencias.add(i.getNombre() + " talla " + i.getTalla() + " color " + i.getColor()
                        + ": solo quedan " + disp + " unidad(es).");
            }
            lineas.add(new LineaCarrito(i.getSku(), i.getProductoId(), i.getNombre(), i.getTalla(), i.getColor(),
                    i.getCantidad(), precio, precioFinal, p == null ? 0 : p.descuentoPorcentaje(),
                    precioFinal * i.getCantidad(), disp, vendible, p != null && p.imagenId() != null ? p.imagenId() : i.getImagenId()));
            subtotal += precio * i.getCantidad();
            descuentos += (precio - precioFinal) * i.getCantidad();
            unidades += i.getCantidad();
        }
        Long envio = null;
        boolean sinCobertura = false;
        if (ciudad != null && !ciudad.isBlank()) {
            Optional<TarifaEnvio> t = tarifas.porCiudad(ciudad);
            if (t.isPresent()) {
                envio = t.get().getCosto();
            } else {
                sinCobertura = true;
            }
        }
        long total = subtotal - descuentos + (envio == null ? 0 : envio);
        return new CarritoDto(lineas, unidades, subtotal, descuentos, envio, envio == null, ciudad, sinCobertura,
                total, advertencias);
    }
}
