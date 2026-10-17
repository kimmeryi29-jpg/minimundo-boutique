package com.minimundo.web.controller;

import com.minimundo.web.api.Api;
import com.minimundo.web.api.ApiException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Asesor de servicio al cliente: bandeja de PQRS (HU-68 a HU-70, HU-74, HU-75), clientes (HU-71) y solicitudes (HU-72, HU-73). */
@Controller
@RequestMapping("/asesor")
public class AsesorController {

    private final Api api;

    public AsesorController(Api api) {
        this.api = api;
    }

    @GetMapping("/pqrs")
    public String bandeja(@RequestParam(required = false) String estado, @RequestParam(required = false) String vencidas,
                          @RequestParam(required = false) String mias, Model model) {
        StringBuilder uri = new StringBuilder("/api/pqrs?x=1");
        List<Object> vars = new java.util.ArrayList<>();
        if (estado != null && !estado.isBlank()) {
            uri.append("&estado={e}");
            vars.add(estado);
        }
        if ("si".equals(vencidas)) {
            uri.append("&vencidas=true");
        }
        if ("si".equals(mias)) {
            uri.append("&asesorId={a}");
            vars.add(((com.minimundo.web.api.SessionUser) org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal()).id());
        }
        model.addAttribute("pqrs", api.lista(uri.toString(), vars.toArray()));
        model.addAttribute("estado", estado);
        model.addAttribute("vencidas", vencidas);
        model.addAttribute("mias", mias);
        model.addAttribute("parametros", api.get("/api/atencion/parametros"));
        return "asesor/pqrs";
    }

    @GetMapping("/pqrs/{id}")
    public String pqrs(@PathVariable String id, Model model) {
        Map<String, Object> q = api.get("/api/pqrs/{id}", id);
        model.addAttribute("q", q);
        if (q.get("pedidoId") != null) {
            try {
                model.addAttribute("pedido", api.get("/api/pedidos/{id}", q.get("pedidoId")));
            } catch (ApiException e) {
                model.addAttribute("pedido", null);
            }
        }
        return "asesor/pqrs-detalle";
    }

    @PostMapping("/pqrs/{id}/tomar")
    public String tomar(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "La PQRS quedó asignada a ti.", () -> api.patch("/api/pqrs/{id}/tomar", null, id));
        return "redirect:/asesor/pqrs/" + id;
    }

    @PostMapping("/pqrs/{id}/responder")
    public String responder(@PathVariable String id, @RequestParam(defaultValue = "") String respuesta, RedirectAttributes ra) {
        Formularios.accion(ra, "Respuesta enviada. La PQRS quedó cerrada.",
                () -> api.patch("/api/pqrs/{id}", Map.of("respuesta", respuesta), id));
        ra.addFlashAttribute("respuesta", respuesta);
        return "redirect:/asesor/pqrs/" + id;
    }

    @GetMapping("/solicitudes")
    public String solicitudes(@RequestParam(defaultValue = "PENDIENTE") String estado, Model model) {
        model.addAttribute("solicitudes", estado.isBlank() ? api.lista("/api/devoluciones")
                : api.lista("/api/devoluciones?estado={e}", estado));
        model.addAttribute("estado", estado);
        return "asesor/solicitudes";
    }

    @PostMapping("/solicitudes/{id}/decision")
    public String decidir(@PathVariable String id, @RequestParam boolean aprobar,
                          @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("aprobar", aprobar);
        body.put("motivo", motivo);
        Formularios.accion(ra, aprobar ? "Solicitud aprobada. Logística recibirá la mercancía." : "Solicitud rechazada.",
                () -> api.patch("/api/devoluciones/{id}/decision", body, id));
        return "redirect:/asesor/solicitudes";
    }

    @GetMapping("/clientes")
    public String clientes(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        if (q != null && !q.isBlank()) {
            model.addAttribute("clientes", api.lista("/api/usuarios/clientes?q={q}", q));
        }
        return "asesor/clientes";
    }

    @GetMapping("/clientes/{id}")
    public String cliente(@PathVariable String id, Model model) {
        model.addAttribute("cliente", api.get("/api/usuarios/{id}", id));
        model.addAttribute("pedidos", api.lista("/api/pedidos?clienteId={id}", id));
        model.addAttribute("solicitudes", api.lista("/api/devoluciones?clienteId={id}", id));
        return "asesor/cliente";
    }
}
