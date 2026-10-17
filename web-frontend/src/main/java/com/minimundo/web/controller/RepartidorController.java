package com.minimundo.web.controller;

import com.minimundo.web.api.Api;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Repartidor: mis entregas (HU-107), en camino (HU-108), entrega (HU-58), fallida (HU-109) e historial (HU-110). */
@Controller
@RequestMapping("/repartidor")
public class RepartidorController {

    private final Api api;

    public RepartidorController(Api api) {
        this.api = api;
    }

    @GetMapping("/entregas")
    public String entregas(Model model) {
        model.addAttribute("entregas", api.lista("/api/entregas/mis-entregas"));
        return "repartidor/entregas";
    }

    @PostMapping("/entregas/{id}/en-ruta")
    public String enRuta(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Marcado en camino. El cliente ya lo ve en su pedido.",
                () -> api.patch("/api/entregas/{id}/en-ruta", null, id));
        return "redirect:/repartidor/entregas";
    }

    @PostMapping("/entregas/{id}/entrega")
    public String entregar(@PathVariable String id, RedirectAttributes ra) {
        Formularios.accion(ra, "Entrega registrada. ¡Buen trabajo!", () -> api.patch("/api/entregas/{id}/entrega", null, id));
        return "redirect:/repartidor/entregas";
    }

    @PostMapping("/entregas/{id}/fallida")
    public String fallida(@PathVariable String id, @RequestParam(defaultValue = "") String motivo,
                          @RequestParam(required = false) String detalle, RedirectAttributes ra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("motivo", motivo);
        body.put("detalle", detalle);
        Formularios.accion(ra, "Registramos la novedad. El operario reprogramará la entrega.",
                () -> api.patch("/api/entregas/{id}/fallida", body, id));
        return "redirect:/repartidor/entregas";
    }

    @GetMapping("/historial")
    public String historial(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                            Model model) {
        String uri = "/api/entregas/historial?desde={d}&hasta={h}";
        model.addAttribute("historial", api.lista(uri, vacio(desde), vacio(hasta)));
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        return "repartidor/historial";
    }

    private static String vacio(String s) {
        return s == null ? "" : s;
    }
}
