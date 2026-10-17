package com.minimundo.web.controller;

import com.minimundo.web.api.Api;
import com.minimundo.web.api.ApiException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operario de catálogo e inventario: productos (HU-16 a HU-25), categorías y colecciones (HU-21), inventario
 * (HU-46 a HU-49, HU-56, HU-60), alistamiento y despacho (HU-50 a HU-57), asignación de repartidor (HU-106),
 * entregas fallidas (HU-109) y reingreso de devoluciones (HU-59).
 */
@Controller
@RequestMapping("/operario")
public class OperarioController {

    private static final Pattern VARIANTE = Pattern.compile("variantes\\[(\\d+)]\\.(talla|color)");

    private final Api api;

    public OperarioController(Api api) {
        this.api = api;
    }

    // ================================================================== Alistamiento y despacho

    @GetMapping("/alistamiento")
    public String alistamiento(Model model) {
        model.addAttribute("cola", api.lista("/api/pedidos?estado=PAGADO"));
        model.addAttribute("enAlistamiento", api.lista("/api/envios?estado=EN_ALISTAMIENTO"));
        return "operario/alistamiento";
    }

    @PostMapping("/alistamiento/tomar")
    public String tomar(@RequestParam String pedidoId, RedirectAttributes ra) {
        Map<String, Object> envio = Formularios.resultado(ra, "Tomaste el pedido. Verifica cada prenda antes de empacar.",
                () -> api.post("/api/envios", Map.of("pedidoId", pedidoId)));
        return envio == null ? "redirect:/operario/alistamiento" : "redirect:/operario/envios/" + envio.get("id");
    }

    @GetMapping("/envios/{id}")
    public String envio(@PathVariable String id, Model model) {
        Map<String, Object> e = api.get("/api/envios/{id}", id);
        model.addAttribute("e", e);
        if ("DESPACHADO".equals(e.get("estado"))) {
            model.addAttribute("repartidores", api.lista("/api/usuarios/repartidores"));
        }
        return "operario/envio";
    }

    @PostMapping("/envios/{id}/verificar")
    public String verificar(@PathVariable String id, @RequestParam String sku, @RequestParam String cantidad,
                            RedirectAttributes ra) {
        Integer c = entero(cantidad);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", sku);
        body.put("cantidad", c);
        Formularios.accion(ra, null, () -> api.patch("/api/envios/{id}/verificacion", body, id));
        return "redirect:/operario/envios/" + id;
    }

    @PostMapping("/envios/{id}/confirmar")
    public String confirmar(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Alistamiento confirmado. Ya puedes registrar el despacho.",
                () -> api.patch("/api/envios/{id}/alistamiento", null, id));
        return "redirect:/operario/envios/" + id;
    }

    @PostMapping("/envios/{id}/despacho")
    public String despachar(@PathVariable String id, @RequestParam(defaultValue = "") String transportadora,
                            @RequestParam(defaultValue = "") String guia, RedirectAttributes ra) {
        Formularios.accion(ra, "Pedido despachado. El cliente ya ve la guía. Ahora asígnale un repartidor.",
                () -> api.patch("/api/envios/{id}/despacho", Map.of("transportadora", transportadora, "guia", guia), id));
        return "redirect:/operario/envios/" + id;
    }

    @PostMapping("/envios/{id}/repartidor")
    public String asignar(@PathVariable String id, @RequestParam(defaultValue = "") String repartidorId,
                          RedirectAttributes ra) {
        Formularios.accion(ra, "Repartidor asignado.",
                () -> api.patch("/api/envios/{id}/repartidor", Map.of("repartidorId", repartidorId), id));
        return "redirect:/operario/envios/" + id;
    }

    @GetMapping("/despachos")
    public String despachos(Model model) {
        List<Map<String, Object>> despachados = api.lista("/api/envios?estado=DESPACHADO");
        model.addAttribute("sinAsignar", despachados.stream().filter(e -> e.get("repartidorId") == null).toList());
        model.addAttribute("fallidas", despachados.stream().filter(e -> Boolean.TRUE.equals(e.get("entregaFallida"))).toList());
        model.addAttribute("asignados", despachados.stream()
                .filter(e -> e.get("repartidorId") != null && !Boolean.TRUE.equals(e.get("entregaFallida"))).toList());
        model.addAttribute("enRuta", api.lista("/api/envios?estado=EN_RUTA"));
        model.addAttribute("repartidores", api.lista("/api/usuarios/repartidores"));
        return "operario/despachos";
    }

    @GetMapping("/devoluciones")
    public String devoluciones(Model model) {
        model.addAttribute("pendientes", api.lista("/api/devoluciones?estado=APROBADA&reingresada=false"));
        model.addAttribute("reingresadas", api.lista("/api/devoluciones?estado=APROBADA&reingresada=true"));
        return "operario/devoluciones";
    }

    @PostMapping("/devoluciones/{id}/reingresar")
    public String reingresar(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Mercancía reingresada al inventario.",
                () -> api.post("/api/inventario/reingresos", Map.of("solicitudId", id)));
        return "redirect:/operario/devoluciones";
    }

    // ================================================================== Productos

    @GetMapping("/productos")
    public String productos(@RequestParam(required = false) String q, @RequestParam(required = false) String estado,
                            Model model) {
        boolean inactivos = "INACTIVO".equals(estado);
        model.addAttribute("productos", api.lista("/api/productos/gestion?q={q}&estado={e}&activo={a}", q == null ? "" : q,
                estado == null || inactivos ? "" : estado, inactivos ? "false" : ""));
        model.addAttribute("q", q);
        model.addAttribute("estado", estado);
        return "operario/productos";
    }

    @GetMapping("/productos/nuevo")
    public String nuevo(Model model) {
        cargarOpciones(model);
        model.addAttribute("p", Map.of("variantes", List.of(Map.of("talla", "", "color", ""))));
        model.addAttribute("nuevo", true);
        return "operario/producto-form";
    }

    @PostMapping("/productos")
    public String crear(@RequestParam MultiValueMap<String, String> f, Model model, RedirectAttributes ra) {
        Map<String, Object> body = producto(f);
        try {
            Map<String, Object> p = api.post("/api/productos", body);
            ra.addFlashAttribute("ok", "Producto creado con el código " + p.get("codigo")
                    + ("PUBLICADO".equals(p.get("estado")) ? " y publicado en el catálogo." : " como borrador. Agrega fotos y publícalo."));
            return "redirect:/operario/productos/" + p.get("id");
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
            cargarOpciones(model);
            model.addAttribute("p", body);
            model.addAttribute("nuevo", true);
            return "operario/producto-form";
        }
    }

    @GetMapping("/productos/{id}")
    public String editar(@PathVariable String id, Model model) {
        cargarOpciones(model);
        model.addAttribute("p", api.get("/api/productos/{id}", id));
        model.addAttribute("nuevo", false);
        return "operario/producto-form";
    }

    @PostMapping("/productos/{id}")
    public String actualizar(@PathVariable String id, @RequestParam MultiValueMap<String, String> f, Model model,
                             RedirectAttributes ra) {
        Map<String, Object> body = producto(f);
        try {
            api.put("/api/productos/{id}", body, id);
            ra.addFlashAttribute("ok", "Cambios guardados. Los pedidos ya creados conservan su precio.");
            return "redirect:/operario/productos/" + id;
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
            cargarOpciones(model);
            Map<String, Object> actual = new LinkedHashMap<>(api.get("/api/productos/{id}", id));
            actual.putAll(body);
            model.addAttribute("p", actual);
            model.addAttribute("nuevo", false);
            return "operario/producto-form";
        }
    }

    @PostMapping("/productos/{id}/{accion:publicar|borrador|desactivar|activar}")
    public String estadoProducto(@PathVariable String id, @PathVariable String accion, RedirectAttributes ra) {
        String mensaje = switch (accion) {
            case "publicar" -> "Producto publicado: ya aparece en el catálogo.";
            case "borrador" -> "El producto volvió a borrador y dejó de verse en el catálogo.";
            case "desactivar" -> "Producto desactivado. Conserva su historial de pedidos e inventario.";
            default -> "Producto activado.";
        };
        Formularios.accion(ra, mensaje, () -> api.patch("/api/productos/{id}/" + accion, null, id));
        return "redirect:/operario/productos/" + id;
    }

    @PostMapping("/productos/{id}/imagenes")
    public String imagenes(@PathVariable String id, @RequestParam("archivos") List<MultipartFile> archivos,
                           RedirectAttributes ra) {
        Formularios.accion(ra, "Imágenes cargadas.", () -> api.subirImagenes("/api/productos/{id}/imagenes", archivos, id));
        return "redirect:/operario/productos/" + id + "#fotos";
    }

    @PostMapping("/productos/{id}/imagenes/{img}/quitar")
    public String quitarImagen(@PathVariable String id, @PathVariable String img, RedirectAttributes ra) {
        Formularios.accion(ra, "Imagen eliminada.", () -> api.delete("/api/productos/{id}/imagenes/{img}", id, img));
        return "redirect:/operario/productos/" + id + "#fotos";
    }

    @PostMapping("/productos/{id}/variantes/{sku}/quitar")
    public String quitarVariante(@PathVariable String id, @PathVariable String sku, RedirectAttributes ra) {
        Formularios.accion(ra, "Variante eliminada.", () -> api.delete("/api/productos/{id}/variantes/{sku}", id, sku));
        return "redirect:/operario/productos/" + id;
    }

    @PostMapping("/productos/{id}/descuento")
    public String descuento(@PathVariable String id, @RequestParam String porcentaje, @RequestParam String inicio,
                            @RequestParam String fin, RedirectAttributes ra) {
        Formularios.accion(ra, "Descuento guardado.",
                () -> api.put("/api/productos/{id}/descuento", descuentoBody(porcentaje, inicio, fin), id));
        return "redirect:/operario/productos/" + id + "#descuento";
    }

    @PostMapping("/productos/{id}/descuento/quitar")
    public String quitarDescuento(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Descuento retirado.", () -> api.delete("/api/productos/{id}/descuento", id));
        return "redirect:/operario/productos/" + id + "#descuento";
    }

    // ================================================================== Categorías y colecciones

    @GetMapping("/categorias")
    public String categorias(Model model) {
        model.addAttribute("categorias", api.lista("/api/categorias"));
        model.addAttribute("opciones", api.get("/api/productos/opciones"));
        return "operario/categorias";
    }

    @PostMapping("/categorias")
    public String crearCategoria(@RequestParam String nombre, @RequestParam String tipo,
                                 @RequestParam(required = false) String descripcion, RedirectAttributes ra) {
        Formularios.accion(ra, "\"" + nombre.trim() + "\" quedó disponible para asignarla a productos.",
                () -> api.post("/api/categorias", cuerpoCategoria(nombre, tipo, descripcion)));
        return "redirect:/operario/categorias";
    }

    @PostMapping("/categorias/{id}")
    public String actualizarCategoria(@PathVariable String id, @RequestParam String nombre, @RequestParam String tipo,
                                      @RequestParam(required = false) String descripcion, RedirectAttributes ra) {
        Formularios.accion(ra, "Cambio guardado. Se refleja en los productos asociados.",
                () -> api.put("/api/categorias/{id}", cuerpoCategoria(nombre, tipo, descripcion), id));
        return "redirect:/operario/categorias";
    }

    @PostMapping("/categorias/{id}/eliminar")
    public String eliminarCategoria(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Eliminada.", () -> api.delete("/api/categorias/{id}", id));
        return "redirect:/operario/categorias";
    }

    @PostMapping("/categorias/{id}/descuento")
    public String descuentoCategoria(@PathVariable String id, @RequestParam String porcentaje,
                                     @RequestParam String inicio, @RequestParam String fin, RedirectAttributes ra) {
        Formularios.accion(ra, "Descuento de la categoría guardado.",
                () -> api.put("/api/categorias/{id}/descuento", descuentoBody(porcentaje, inicio, fin), id));
        return "redirect:/operario/categorias";
    }

    @PostMapping("/categorias/{id}/descuento/quitar")
    public String quitarDescuentoCategoria(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Descuento retirado.", () -> api.delete("/api/categorias/{id}/descuento", id));
        return "redirect:/operario/categorias";
    }

    // ================================================================== Inventario

    @GetMapping("/inventario")
    public String inventario(@RequestParam(required = false) String sku, @RequestParam(required = false) String talla,
                             @RequestParam(required = false) String color, @RequestParam(required = false) String filtro,
                             Model model) {
        model.addAttribute("inv", api.get("/api/inventario?sku={s}&talla={t}&color={c}&filtro={f}", vacio(sku),
                vacio(talla), vacio(color), vacio(filtro)));
        model.addAttribute("sku", sku);
        model.addAttribute("talla", talla);
        model.addAttribute("color", color);
        model.addAttribute("filtro", filtro);
        return "operario/inventario";
    }

    @PostMapping("/inventario/entrada")
    public String entrada(@RequestParam String sku, @RequestParam String cantidad,
                          @RequestParam(required = false) String motivo, @RequestParam(required = false) String volver,
                          RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", sku.trim());
        body.put("cantidad", entero(cantidad));
        body.put("motivo", motivo);
        Formularios.accion(ra, "Entrada registrada en " + sku.trim().toUpperCase() + ".",
                () -> api.post("/api/inventario/entradas", body));
        return destino(volver, sku);
    }

    @PostMapping("/inventario/ajuste")
    public String ajuste(@RequestParam String sku, @RequestParam String cantidad, @RequestParam(defaultValue = "") String motivo,
                         @RequestParam(required = false) String volver, RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", sku.trim());
        body.put("cantidad", entero(cantidad));
        body.put("motivo", motivo);
        Formularios.accion(ra, "Ajuste registrado en " + sku.trim().toUpperCase() + ".",
                () -> api.post("/api/inventario/ajustes", body));
        return destino(volver, sku);
    }

    @GetMapping("/inventario/{sku}")
    @SuppressWarnings("unchecked")
    public String movimientos(@PathVariable String sku, @RequestParam(required = false) String tipo,
                              @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                              Model model) {
        Map<String, Object> inv = api.get("/api/inventario?sku={s}", sku);
        List<Map<String, Object>> items = (List<Map<String, Object>>) inv.get("items");
        model.addAttribute("item", items.stream().filter(i -> sku.equalsIgnoreCase(String.valueOf(i.get("sku"))))
                .findFirst().orElse(null));
        model.addAttribute("movimientos", api.lista("/api/inventario/{s}/movimientos?tipo={t}&desde={d}&hasta={h}", sku,
                vacio(tipo), vacio(desde), vacio(hasta)));
        model.addAttribute("sku", sku);
        model.addAttribute("tipo", tipo);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        return "operario/movimientos";
    }

    @PostMapping("/inventario/{sku}/minimo")
    public String minimo(@PathVariable String sku, @RequestParam(required = false) String stockMinimo, RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stockMinimo", stockMinimo == null || stockMinimo.isBlank() ? null : entero(stockMinimo));
        Formularios.accion(ra, "Stock mínimo actualizado.", () -> api.put("/api/inventario/{s}/minimo", body, sku));
        return "redirect:/operario/inventario/" + UriUtils.encodePathSegment(sku, StandardCharsets.UTF_8);
    }

    // ================================================================== Apoyo

    private void cargarOpciones(Model model) {
        model.addAttribute("opciones", api.get("/api/productos/opciones"));
        model.addAttribute("categorias", api.lista("/api/categorias?tipo=CATEGORIA"));
        List<Map<String, Object>> colecciones = new ArrayList<>(api.lista("/api/categorias?tipo=EDAD"));
        colecciones.addAll(api.lista("/api/categorias?tipo=TEMPORADA"));
        model.addAttribute("colecciones", colecciones);
    }

    private Map<String, Object> producto(MultiValueMap<String, String> f) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", f.getFirst("nombre"));
        body.put("descripcion", f.getFirst("descripcion"));
        body.put("categoriaId", f.getFirst("categoriaId"));
        body.put("coleccionIds", f.getOrDefault("coleccionIds", List.of()));
        body.put("genero", f.getFirst("genero"));
        body.put("rangoEdad", f.getFirst("rangoEdad"));
        String precio = f.getFirst("precio");
        Long valor = null;
        if (precio != null && !precio.isBlank()) {
            try {
                valor = Long.parseLong(precio.replaceAll("[^0-9-]", ""));
            } catch (NumberFormatException ignorada) {
                valor = null;
            }
        }
        body.put("precio", valor);
        Map<Integer, Map<String, String>> filas = new TreeMap<>();
        f.forEach((k, v) -> {
            Matcher m = VARIANTE.matcher(k);
            if (m.matches()) {
                filas.computeIfAbsent(Integer.parseInt(m.group(1)), x -> new LinkedHashMap<>()).put(m.group(2), v.get(0));
            }
        });
        List<Map<String, String>> variantes = filas.values().stream()
                .filter(x -> !(blanco(x.get("talla")) && blanco(x.get("color")))).toList();
        body.put("variantes", variantes);
        body.put("publicar", "si".equals(f.getFirst("publicar")));
        return body;
    }

    private static Map<String, Object> descuentoBody(String porcentaje, String inicio, String fin) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("porcentaje", entero(porcentaje));
        body.put("inicio", blanco(inicio) ? null : inicio);
        body.put("fin", blanco(fin) ? null : fin);
        return body;
    }

    private static Map<String, Object> cuerpoCategoria(String nombre, String tipo, String descripcion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", nombre);
        body.put("tipo", tipo);
        body.put("descripcion", descripcion);
        return body;
    }

    private static String destino(String volver, String sku) {
        if ("movimientos".equals(volver)) {
            return "redirect:/operario/inventario/" + UriUtils.encodePathSegment(sku.trim().toUpperCase(), StandardCharsets.UTF_8);
        }
        return "redirect:/operario/inventario";
    }

    static Integer entero(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean blanco(String s) {
        return s == null || s.isBlank();
    }

    private static String vacio(String s) {
        return s == null ? "" : s;
    }
}
