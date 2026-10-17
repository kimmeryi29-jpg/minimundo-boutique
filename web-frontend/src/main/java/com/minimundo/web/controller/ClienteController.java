package com.minimundo.web.controller;

import com.minimundo.web.api.Api;
import com.minimundo.web.api.ApiException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vistas del cliente: carrito (HU-31 a HU-34), checkout con dirección, envío y pago simulado (HU-36 a HU-41),
 * mis pedidos y su seguimiento (HU-42 a HU-44, HU-111), perfil (HU-5), PQRS (HU-61 a HU-63) y cambios o
 * devoluciones (HU-64 a HU-67).
 */
@Controller
public class ClienteController {

    private final Api api;

    public ClienteController(Api api) {
        this.api = api;
    }

    // ------------------------------------------------------------------ Carrito

    @PostMapping("/carrito/agregar")
    public String agregar(@RequestParam(required = false) String sku, @RequestParam(defaultValue = "1") int cantidad,
                          @RequestParam String productoId, RedirectAttributes ra) {
        if (sku == null || sku.isBlank()) {
            ra.addFlashAttribute("error", "Elige la talla y el color antes de agregar al carrito.");
            return "redirect:/producto/" + productoId;
        }
        boolean ok = Formularios.resultado(ra, "Agregamos el producto a tu carrito.",
                () -> api.post("/api/carrito/items", Map.of("sku", sku, "cantidad", cantidad))) != null;
        return ok ? "redirect:/carrito" : "redirect:/producto/" + productoId;
    }

    @GetMapping("/carrito")
    public String carrito(Model model) {
        model.addAttribute("carrito", api.get("/api/carrito"));
        return "cliente/carrito";
    }

    @PostMapping("/carrito/{sku}/cantidad")
    public String cantidad(@PathVariable String sku, @RequestParam String cantidad, RedirectAttributes ra) {
        int valor;
        try {
            valor = Integer.parseInt(cantidad.trim());
        } catch (NumberFormatException e) {
            ra.addFlashAttribute("error", "La cantidad debe ser un número entero.");
            return "redirect:/carrito";
        }
        Formularios.accion(ra, null, () -> api.put("/api/carrito/items/{sku}", Map.of("cantidad", valor), sku));
        return "redirect:/carrito";
    }

    @PostMapping("/carrito/{sku}/quitar")
    public String quitar(@PathVariable String sku, RedirectAttributes ra) {
        Formularios.accion(ra, "Quitamos el producto del carrito.", () -> api.delete("/api/carrito/items/{sku}", sku));
        return "redirect:/carrito";
    }

    // ------------------------------------------------------------------ Checkout

    @GetMapping("/checkout")
    @SuppressWarnings("unchecked")
    public String checkout(@RequestParam(required = false) String ciudad, Model model) {
        Map<String, Object> perfil = api.get("/api/usuarios/me");
        Map<String, Object> guardada = (Map<String, Object>) perfil.get("direccion");
        String ciudadEnvio = ciudad != null ? ciudad : guardada != null ? (String) guardada.get("ciudad") : null;
        Map<String, Object> carrito = api.get("/api/carrito?ciudad={c}", ciudadEnvio == null ? "" : ciudadEnvio);
        if (((List<?>) carrito.get("items")).isEmpty()) {
            return "redirect:/carrito";
        }
        model.addAttribute("carrito", carrito);
        model.addAttribute("perfil", perfil);
        model.addAttribute("guardada", guardada);
        model.addAttribute("ciudad", ciudadEnvio);
        model.addAttribute("tarifas", api.lista("/api/tarifas-envio"));
        return "cliente/checkout";
    }

    @PostMapping("/checkout")
    public String pagar(@RequestParam Map<String, String> f, Model model, RedirectAttributes ra) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("nombreRecibe", f.getOrDefault("nombreRecibe", ""));
        direccion.put("direccion", f.getOrDefault("direccion", ""));
        direccion.put("barrio", f.getOrDefault("barrio", ""));
        direccion.put("ciudad", f.getOrDefault("ciudad", ""));
        direccion.put("telefono", f.getOrDefault("telefono", ""));
        direccion.put("indicaciones", f.getOrDefault("indicaciones", ""));
        Map<String, Object> pago = new LinkedHashMap<>();
        pago.put("titular", f.getOrDefault("titular", ""));
        pago.put("numeroTarjeta", f.getOrDefault("numeroTarjeta", ""));
        pago.put("vencimiento", f.getOrDefault("vencimiento", ""));
        pago.put("cvv", f.getOrDefault("cvv", ""));
        try {
            Map<String, Object> pedido = api.post("/api/pedidos", Map.of("direccion", direccion, "pago", pago));
            if ("si".equals(f.get("guardarDireccion"))) {
                guardarDireccion(direccion);
            }
            return "redirect:/cuenta/pedidos/" + pedido.get("id") + "?confirmado";
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                throw e;
            }
            // HU-36 y HU-40: el carrito se conserva y se muestra el motivo
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("campos", e.getCampos());
            ra.addFlashAttribute("form", f);
            String c = f.getOrDefault("ciudad", "");
            return e.getStatus() == 409 ? "redirect:/carrito"
                    : "redirect:/checkout?ciudad=" + UriUtils.encode(c, StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private void guardarDireccion(Map<String, Object> d) {
        try {
            Map<String, Object> perfil = api.get("/api/usuarios/me");
            Map<String, Object> dir = new LinkedHashMap<>();
            dir.put("direccion", d.get("direccion"));
            dir.put("barrio", d.get("barrio"));
            dir.put("ciudad", d.get("ciudad"));
            dir.put("telefono", d.get("telefono"));
            dir.put("indicaciones", d.get("indicaciones"));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("nombre", perfil.get("nombre"));
            body.put("telefono", perfil.get("telefono"));
            body.put("direccion", dir);
            api.put("/api/usuarios/me", body);
        } catch (ApiException ignorada) {
            // guardar la dirección es opcional; el pedido ya quedó creado
        }
    }

    // ------------------------------------------------------------------ Pedidos

    @GetMapping("/cuenta/pedidos")
    public String pedidos(Model model) {
        model.addAttribute("pedidos", api.lista("/api/pedidos/mis-pedidos"));
        return "cliente/pedidos";
    }

    @GetMapping("/cuenta/pedidos/{id}")
    public String pedido(@PathVariable String id, @RequestParam(required = false) String confirmado, Model model) {
        model.addAttribute("pedido", api.get("/api/pedidos/{id}", id));
        model.addAttribute("confirmado", confirmado != null);
        try {
            model.addAttribute("solicitudes", api.lista("/api/devoluciones/mias").stream()
                    .filter(s -> id.equals(s.get("pedidoId"))).toList());
            model.addAttribute("plazo", api.get("/api/atencion/parametros").get("plazoCambiosDias"));
        } catch (ApiException e) {
            model.addAttribute("solicitudes", List.of());
        }
        return "cliente/pedido";
    }

    @PostMapping("/cuenta/pedidos/{id}/cancelar")
    public String cancelar(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Cancelamos tu pedido. Si ya habías pagado, no se realizó ningún cobro real.",
                () -> api.patch("/api/pedidos/{id}/cancelar", null, id));
        return "redirect:/cuenta/pedidos/" + id;
    }

    // ------------------------------------------------------------------ Perfil

    @GetMapping("/cuenta/perfil")
    public String perfil(Model model) {
        model.addAttribute("perfil", api.get("/api/usuarios/me"));
        model.addAttribute("tarifas", api.lista("/api/tarifas-envio"));
        return "cliente/perfil";
    }

    @PostMapping("/cuenta/perfil")
    public String guardarPerfil(@RequestParam Map<String, String> f, Model model, RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", f.getOrDefault("nombre", ""));
        body.put("telefono", f.getOrDefault("telefono", ""));
        boolean conDireccion = !f.getOrDefault("direccion", "").isBlank() || !f.getOrDefault("ciudad", "").isBlank();
        if (conDireccion) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("direccion", f.getOrDefault("direccion", ""));
            d.put("barrio", f.getOrDefault("barrio", ""));
            d.put("ciudad", f.getOrDefault("ciudad", ""));
            d.put("telefono", f.getOrDefault("telefonoEntrega", ""));
            d.put("indicaciones", f.getOrDefault("indicaciones", ""));
            body.put("direccion", d);
        }
        try {
            api.put("/api/usuarios/me", body);
            ra.addFlashAttribute("ok", "Guardamos tus datos.");
            return "redirect:/cuenta/perfil";
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
            model.addAttribute("perfil", api.get("/api/usuarios/me"));
            model.addAttribute("form", f);
            model.addAttribute("tarifas", api.lista("/api/tarifas-envio"));
            return "cliente/perfil";
        }
    }

    // ------------------------------------------------------------------ PQRS

    @GetMapping("/cuenta/pqrs")
    public String pqrs(Model model) {
        model.addAttribute("pqrs", api.lista("/api/pqrs/mias"));
        return "cliente/pqrs";
    }

    @GetMapping("/cuenta/pqrs/nueva")
    public String nuevaPqrs(@RequestParam(required = false) String pedidoId, Model model) {
        model.addAttribute("pedidos", api.lista("/api/pedidos/mis-pedidos"));
        model.addAttribute("pedidoId", pedidoId);
        return "cliente/pqrs-nueva";
    }

    @PostMapping("/cuenta/pqrs")
    public String radicar(@RequestParam(defaultValue = "") String tipo, @RequestParam(defaultValue = "") String descripcion,
                          @RequestParam(required = false) String pedidoId, Model model, RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tipo", tipo);
        body.put("descripcion", descripcion);
        body.put("pedidoId", pedidoId == null || pedidoId.isBlank() ? null : pedidoId);
        try {
            Map<String, Object> r = api.post("/api/pqrs", body);
            ra.addFlashAttribute("ok", "Radicamos tu caso con el número " + r.get("radicado")
                    + ". Te responderemos en máximo 5 días hábiles.");
            return "redirect:/cuenta/pqrs/" + r.get("id");
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
            model.addAttribute("pedidos", api.lista("/api/pedidos/mis-pedidos"));
            model.addAttribute("pedidoId", pedidoId);
            model.addAttribute("tipo", tipo);
            model.addAttribute("descripcion", descripcion);
            return "cliente/pqrs-nueva";
        }
    }

    @GetMapping("/cuenta/pqrs/{id}")
    public String verPqrs(@PathVariable String id, Model model) {
        model.addAttribute("q", api.get("/api/pqrs/{id}", id));
        return "cliente/pqrs-detalle";
    }

    // ------------------------------------------------------------------ Cambios y devoluciones

    @GetMapping("/cuenta/solicitudes")
    public String solicitudes(Model model) {
        model.addAttribute("solicitudes", api.lista("/api/devoluciones/mias"));
        return "cliente/solicitudes";
    }

    @GetMapping("/cuenta/pedidos/{id}/solicitud")
    @SuppressWarnings("unchecked")
    public String nuevaSolicitud(@PathVariable String id, @RequestParam String sku,
                                 @RequestParam(defaultValue = "DEVOLUCION") String tipo, Model model) {
        Map<String, Object> pedido = api.get("/api/pedidos/{id}", id);
        Map<String, Object> item = ((List<Map<String, Object>>) pedido.get("items")).stream()
                .filter(i -> sku.equals(i.get("sku"))).findFirst()
                .orElseThrow(() -> new ApiException(404, "El producto no hace parte del pedido", null));
        model.addAttribute("pedido", pedido);
        model.addAttribute("item", item);
        model.addAttribute("tipo", tipo);
        try {
            Map<String, Object> producto = api.get("/api/productos/{id}", item.get("productoId"));
            model.addAttribute("tallas", ((List<String>) producto.get("tallas")).stream()
                    .filter(t -> !t.equalsIgnoreCase(String.valueOf(item.get("talla")))).toList());
        } catch (ApiException e) {
            model.addAttribute("tallas", List.of());
        }
        model.addAttribute("plazo", api.get("/api/atencion/parametros").get("plazoCambiosDias"));
        return "cliente/solicitud-nueva";
    }

    @PostMapping("/cuenta/pedidos/{id}/solicitud")
    public String crearSolicitud(@PathVariable String id, @RequestParam String sku, @RequestParam String tipo,
                                 @RequestParam(defaultValue = "1") int cantidad,
                                 @RequestParam(required = false) String tallaDeseada,
                                 @RequestParam(defaultValue = "") String motivo, RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tipo", tipo);
        body.put("pedidoId", id);
        body.put("sku", sku);
        body.put("cantidad", cantidad);
        body.put("tallaDeseada", tallaDeseada);
        body.put("motivo", motivo);
        Map<String, Object> r = Formularios.resultado(ra, null, () -> api.post("/api/devoluciones", body));
        if (r == null) {
            ra.addFlashAttribute("motivo", motivo);
            return "redirect:/cuenta/pedidos/" + id + "/solicitud?sku=" + UriUtils.encode(sku, StandardCharsets.UTF_8)
                    + "&tipo=" + tipo;
        }
        ra.addFlashAttribute("ok", "Recibimos tu solicitud " + r.get("numero") + ". Un asesor la revisará pronto.");
        return "redirect:/cuenta/solicitudes";
    }
}
