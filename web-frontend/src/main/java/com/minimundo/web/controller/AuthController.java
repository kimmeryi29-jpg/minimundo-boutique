package com.minimundo.web.controller;

import com.minimundo.web.api.Api;
import com.minimundo.web.api.ApiException;
import com.minimundo.web.config.SecurityConfig;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Inicio de sesión (HU-2, HU-3), registro (HU-1, HU-7) y recuperación de contraseña (HU-6). */
@Controller
public class AuthController {

    private final Api api;

    public AuthController(Api api) {
        this.api = api;
    }

    @GetMapping("/login")
    public String login(HttpSession session, Model model, @RequestParam(required = false) String expirada,
                        @RequestParam(required = false) String registrado) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof com.minimundo.web.api.SessionUser u) {
            return "redirect:" + u.inicio();
        }
        model.addAttribute("errorLogin", session.getAttribute(SecurityConfig.LOGIN_ERROR));
        model.addAttribute("email", session.getAttribute(SecurityConfig.LOGIN_EMAIL));
        session.removeAttribute(SecurityConfig.LOGIN_ERROR);
        if (expirada != null) {
            model.addAttribute("info", "Tu sesión expiró. Inicia sesión de nuevo.");
        }
        return "auth/login";
    }

    @GetMapping("/registro")
    public String registro() {
        return "auth/registro";
    }

    @PostMapping("/registro")
    public String registrar(@RequestParam String nombre, @RequestParam String email, @RequestParam String password,
                            @RequestParam String confirmacion, Model model, RedirectAttributes ra) {
        model.addAttribute("nombre", nombre);
        model.addAttribute("email", email);
        if (!password.equals(confirmacion)) {
            model.addAttribute("error", "Las contraseñas no coinciden");
            model.addAttribute("campos", Map.of("confirmacion", "Las contraseñas no coinciden"));
            return "auth/registro";
        }
        try {
            api.post("/api/auth/registro", Map.of("nombre", nombre, "email", email, "password", password));
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
            return "auth/registro";
        }
        ra.addFlashAttribute("ok", "Tu cuenta quedó creada. Ya puedes iniciar sesión y comprar.");
        ra.addFlashAttribute("email", email);
        return "redirect:/login";
    }

    @GetMapping("/recuperar")
    public String recuperar() {
        return "auth/recuperar";
    }

    @PostMapping("/recuperar")
    public String solicitar(@RequestParam String email, Model model) {
        try {
            Map<String, Object> r = api.post("/api/auth/recuperar", Map.of("email", email));
            model.addAttribute("ok", r.get("mensaje"));
            model.addAttribute("enlaceDemo", r.get("enlaceDemo"));
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
        }
        model.addAttribute("email", email);
        return "auth/recuperar";
    }

    @GetMapping("/restablecer")
    public String restablecer(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "auth/restablecer";
    }

    @PostMapping("/restablecer")
    public String guardarClave(@RequestParam String token, @RequestParam String password,
                               @RequestParam String confirmacion, Model model, RedirectAttributes ra) {
        model.addAttribute("token", token);
        if (!password.equals(confirmacion)) {
            model.addAttribute("error", "Las contraseñas no coinciden");
            return "auth/restablecer";
        }
        try {
            api.post("/api/auth/restablecer", Map.of("token", token, "password", password));
        } catch (ApiException e) {
            Formularios.conErrores(model, e);
            return "auth/restablecer";
        }
        ra.addFlashAttribute("ok", "Tu contraseña quedó actualizada. Inicia sesión con la nueva.");
        return "redirect:/login";
    }
}
