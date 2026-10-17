package com.minimundo.web.controller;

import com.minimundo.web.api.Api;
import com.minimundo.web.api.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrador: panel de indicadores (HU-76 a HU-80, HU-88, HU-90), reportes y exportación (HU-45, HU-85, HU-86),
 * pedidos e historial de estados (HU-87), usuarios y roles (HU-8 a HU-11), auditoría (HU-15) y parámetros del
 * sistema en una sola pantalla (HU-81 a HU-84, HU-89).
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final Api api;

    public AdminController(Api api) {
        this.api = api;
    }

    // ------------------------------------------------------------------ Panel

    @GetMapping
    public String panel(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                        Model model) {
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        try {
            model.addAttribute("r", api.get("/api/reportes/resumen?desde={d}&hasta={h}", vacio(desde), vacio(hasta)));
        } catch (ApiException e) {
            if (e.getStatus() == 400) {
                model.addAttribute("error", e.getMessage());
                model.addAttribute("r", api.get("/api/reportes/resumen"));
            } else {
                throw e;
            }
        }
        try {
            model.addAttribute("sinVentas", api.lista("/api/reportes/sin-ventas?dias=30"));
        } catch (ApiException e) {
            model.addAttribute("sinVentas", List.of());
            model.addAttribute("avisoSinVentas", e.getMessage());
        }
        try {
            model.addAttribute("alertas", api.lista("/api/inventario/bajo-stock").size());
        } catch (ApiException e) {
            model.addAttribute("alertas", null);
        }
        return "admin/panel";
    }

    // ------------------------------------------------------------------ Reportes

    @GetMapping("/reportes")
    public String reportes(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                           Model model) {
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        try {
            model.addAttribute("ventas", api.get("/api/reportes/ventas?desde={d}&hasta={h}", vacio(desde), vacio(hasta)));
            model.addAttribute("pqrs", api.get("/api/pqrs/reporte?desde={d}&hasta={h}", vacio(desde), vacio(hasta)));
        } catch (ApiException e) {
            if (e.getStatus() != 400) {
                throw e;
            }
            model.addAttribute("error", e.getMessage());
            model.addAttribute("ventas", api.get("/api/reportes/ventas"));
            model.addAttribute("pqrs", api.get("/api/pqrs/reporte"));
        }
        return "admin/reportes";
    }

    @GetMapping("/reportes/ventas.csv")
    public Object exportarVentas(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                                 RedirectAttributes ra) {
        return csv("/api/reportes/ventas/exportar?desde={d}&hasta={h}", "ventas.csv", ra, vacio(desde), vacio(hasta));
    }

    @GetMapping("/reportes/inventario.csv")
    public Object exportarInventario(RedirectAttributes ra) {
        return csv("/api/inventario/exportar", "inventario.csv", ra);
    }

    /** HU-86: si no hay datos se informa y no se descarga un archivo vacío. */
    private Object csv(String uri, String nombre, RedirectAttributes ra, Object... vars) {
        try {
            ResponseEntity<byte[]> r = api.descargar(uri, vars);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(r.getBody());
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                throw e;
            }
            ra.addFlashAttribute("error", e.getStatus() == 404 ? "No hay datos para exportar en el periodo seleccionado."
                    : e.getMessage());
            return "redirect:/admin/reportes";
        }
    }

    // ------------------------------------------------------------------ Pedidos (HU-87)

    @GetMapping("/pedidos")
    public String pedidos(@RequestParam(required = false) String codigo, @RequestParam(required = false) String estado,
                          Model model) {
        model.addAttribute("pedidos", api.lista("/api/pedidos?codigo={c}&estado={e}", vacio(codigo), vacio(estado)));
        model.addAttribute("codigo", codigo);
        model.addAttribute("estado", estado);
        return "admin/pedidos";
    }

    @GetMapping("/pedidos/{id}")
    public String pedido(@PathVariable String id, Model model) {
        model.addAttribute("pedido", api.get("/api/pedidos/{id}", id));
        return "admin/pedido";
    }

    // ------------------------------------------------------------------ Usuarios

    @GetMapping("/usuarios")
    public String usuarios(@RequestParam(required = false) String rol, @RequestParam(required = false) String activo,
                           @RequestParam(required = false) String q, Model model) {
        model.addAttribute("usuarios", api.lista("/api/usuarios?rol={r}&activo={a}&q={q}", vacio(rol), vacio(activo), vacio(q)));
        model.addAttribute("rol", rol);
        model.addAttribute("activo", activo);
        model.addAttribute("q", q);
        return "admin/usuarios";
    }

    @GetMapping("/usuarios/nuevo")
    public String nuevoUsuario(Model model) {
        model.addAttribute("u", Map.of("rol", "ASESOR"));
        model.addAttribute("nuevo", true);
        return "admin/usuario-form";
    }

    @PostMapping("/usuarios")
    public String crearUsuario(@RequestParam Map<String, String> f, Model model, RedirectAttributes ra) {
        try {
            Map<String, Object> u = api.post("/api/usuarios", Map.of("nombre", f.getOrDefault("nombre", ""),
                    "email", f.getOrDefault("email", ""), "password", f.getOrDefault("password", ""),
                    "rol", f.getOrDefault("rol", ""), "telefono", f.getOrDefault("telefono", "")));
            ra.addFlashAttribute("ok", u.get("nombre") + " ya puede iniciar sesión como " + u.get("rol") + ".");
            return "redirect:/admin/usuarios";
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
            model.addAttribute("u", f);
            model.addAttribute("nuevo", true);
            return "admin/usuario-form";
        }
    }

    @GetMapping("/usuarios/{id}")
    public String editarUsuario(@PathVariable String id, Model model) {
        model.addAttribute("u", api.get("/api/usuarios/{id}", id));
        model.addAttribute("nuevo", false);
        return "admin/usuario-form";
    }

    @PostMapping("/usuarios/{id}")
    public String actualizarUsuario(@PathVariable String id, @RequestParam Map<String, String> f, Model model,
                                    RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", f.getOrDefault("nombre", ""));
        body.put("email", f.getOrDefault("email", ""));
        body.put("rol", f.getOrDefault("rol", ""));
        body.put("telefono", f.getOrDefault("telefono", ""));
        try {
            api.put("/api/usuarios/{id}", body, id);
            ra.addFlashAttribute("ok", "Cambios guardados. Si cambiaste el rol, aplica desde su próximo inicio de sesión.");
            return "redirect:/admin/usuarios";
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
            Map<String, Object> u = new LinkedHashMap<>(api.get("/api/usuarios/{id}", id));
            u.putAll(body);
            model.addAttribute("u", u);
            model.addAttribute("nuevo", false);
            return "admin/usuario-form";
        }
    }

    @PostMapping("/usuarios/{id}/{accion:activar|desactivar}")
    public String estadoUsuario(@PathVariable String id, @PathVariable String accion, RedirectAttributes ra) {
        Formularios.accion(ra, "desactivar".equals(accion)
                        ? "Usuario desactivado. Su historial se conserva y ya no puede iniciar sesión."
                        : "Usuario activado.",
                () -> api.patch("/api/usuarios/{id}/" + accion, null, id));
        return "redirect:/admin/usuarios";
    }

    // ------------------------------------------------------------------ Auditoría (HU-15)

    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                            @RequestParam(required = false) String usuario, @RequestParam(required = false) String tipo,
                            Model model) {
        model.addAttribute("eventos", api.lista("/api/auditoria?desde={d}&hasta={h}&usuario={u}&tipo={t}", vacio(desde),
                vacio(hasta), vacio(usuario), vacio(tipo)));
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("usuario", usuario);
        model.addAttribute("tipo", tipo);
        return "admin/auditoria";
    }

    // ------------------------------------------------------------------ Parámetros (HU-89)

    @GetMapping("/parametros")
    public String parametros(Model model) {
        model.addAttribute("tarifas", api.lista("/api/tarifas-envio"));
        model.addAttribute("logistica", api.get("/api/inventario/parametros"));
        model.addAttribute("atencion", api.get("/api/atencion/parametros"));
        return "admin/parametros";
    }

    @PostMapping("/parametros/tarifas")
    public String crearTarifa(@RequestParam String ciudad, @RequestParam String costo, RedirectAttributes ra) {
        Formularios.accion(ra, "Tarifa de " + ciudad.trim() + " guardada. El checkout ya usa el nuevo valor.",
                () -> api.post("/api/tarifas-envio", tarifa(ciudad, costo)));
        return "redirect:/admin/parametros#tarifas";
    }

    @PostMapping("/parametros/tarifas/{id}")
    public String actualizarTarifa(@PathVariable String id, @RequestParam String ciudad, @RequestParam String costo,
                                   RedirectAttributes ra) {
        Formularios.accion(ra, "Tarifa de " + ciudad.trim() + " actualizada. El checkout ya usa el nuevo valor.",
                () -> api.put("/api/tarifas-envio/{id}", tarifa(ciudad, costo), id));
        return "redirect:/admin/parametros#tarifas";
    }

    @PostMapping("/parametros/tarifas/{id}/eliminar")
    public String eliminarTarifa(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Ciudad eliminada de la cobertura.", () -> api.delete("/api/tarifas-envio/{id}", id));
        return "redirect:/admin/parametros#tarifas";
    }

    @PostMapping("/parametros/stock-minimo")
    public String stockMinimo(@RequestParam String stockMinimoDefecto, RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stockMinimoDefecto", OperarioController.entero(stockMinimoDefecto));
        Formularios.accion(ra, "Stock mínimo por defecto actualizado.", () -> api.put("/api/inventario/parametros", body));
        return "redirect:/admin/parametros#inventario";
    }

    @PostMapping("/parametros/atencion")
    public String atencion(@RequestParam String plazoCambiosDias, @RequestParam String diasVencimientoPqrs,
                           RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plazoCambiosDias", OperarioController.entero(plazoCambiosDias));
        body.put("diasVencimientoPqrs", OperarioController.entero(diasVencimientoPqrs));
        Formularios.accion(ra, "Parámetros de atención actualizados. Aplican a las solicitudes nuevas.",
                () -> api.put("/api/atencion/parametros", body));
        return "redirect:/admin/parametros#atencion";
    }

    private static Map<String, Object> tarifa(String ciudad, String costo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ciudad", ciudad);
        Long valor = null;
        try {
            valor = Long.parseLong(costo.replaceAll("[.$\\s]", ""));
        } catch (NumberFormatException ignorada) {
            valor = null;
        }
        body.put("costo", valor);
        return body;
    }

    private static String vacio(String s) {
        return s == null ? "" : s;
    }
}
