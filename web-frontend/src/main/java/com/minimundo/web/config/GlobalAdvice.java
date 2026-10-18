package com.minimundo.web.config;

import com.minimundo.web.api.ApiException;
import com.minimundo.web.api.Despertador;
import com.minimundo.web.api.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Datos comunes de todas las vistas y manejo de errores de la API: sesión vencida (401), sin permiso (403),
 * no encontrado (404) y servicio no disponible (503, HU-90/HU-103).
 */
@ControllerAdvice
public class GlobalAdvice {

    private final boolean mostrarDemo;
    private final String demoPassword;
    private final Despertador despertador;

    public GlobalAdvice(@Value("${minimundo.mostrar-usuarios-demo:true}") boolean mostrarDemo,
                        @Value("${minimundo.demo-password:Demo12345}") String demoPassword, Despertador despertador) {
        this.mostrarDemo = mostrarDemo;
        this.despertador = despertador;
        this.demoPassword = demoPassword;
    }

    @ModelAttribute
    public void comunes(Model model, HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SessionUser u = auth != null && auth.getPrincipal() instanceof SessionUser s ? s : null;
        model.addAttribute("yo", u);
        model.addAttribute("ruta", req.getRequestURI());
        model.addAttribute("mostrarDemo", mostrarDemo);
        model.addAttribute("demoPassword", demoPassword);
        model.addAttribute("servicios", despertador.getUrls());
    }

    @ExceptionHandler(ApiException.class)
    public String api(ApiException e, HttpServletRequest req, Model model) {
        if (e.getStatus() == 401 && SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof SessionUser) {
            req.getSession().invalidate();
            SecurityContextHolder.clearContext();
            return "redirect:/login?expirada";
        }
        comunes(model, req);
        model.addAttribute("codigo", e.getStatus());
        model.addAttribute("esGet", "GET".equalsIgnoreCase(req.getMethod()));
        model.addAttribute("mensaje", e.getMessage());
        return "error/general";
    }
}
