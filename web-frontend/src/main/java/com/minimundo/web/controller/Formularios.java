package com.minimundo.web.controller;

import com.minimundo.web.api.ApiException;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Supplier;

/** Utilidades para mostrar en las vistas los mensajes y los campos con error que devuelve la API. */
final class Formularios {

    private Formularios() {
    }

    /** Ejecuta la acción y deja un mensaje de éxito o el error de la API para la siguiente vista. */
    static void accion(RedirectAttributes ra, String exito, Runnable accion) {
        try {
            accion.run();
            if (exito != null) {
                ra.addFlashAttribute("ok", exito);
            }
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                throw e;
            }
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("campos", e.getCampos());
        }
    }

    /** Igual que {@link #accion} pero devuelve el resultado o null si falló. */
    static <T> T resultado(RedirectAttributes ra, String exito, Supplier<T> accion) {
        try {
            T r = accion.get();
            if (exito != null) {
                ra.addFlashAttribute("ok", exito);
            }
            return r;
        } catch (ApiException e) {
            if (e.getStatus() == 401) {
                throw e;
            }
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("campos", e.getCampos());
            return null;
        }
    }

    /** Para formularios que se vuelven a mostrar con los errores en la misma petición. */
    static boolean conErrores(Model model, ApiException e) {
        if (e.getStatus() == 401) {
            throw e;
        }
        model.addAttribute("error", e.getMessage());
        model.addAttribute("campos", e.getCampos());
        return true;
    }

    static String texto(Object o) {
        return o == null ? "" : o.toString();
    }
}
