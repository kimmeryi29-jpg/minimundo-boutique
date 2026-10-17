package com.minimundo.gateway;

import org.springframework.http.HttpMethod;

import java.util.regex.Pattern;

/** HU-92: rutas que no exigen token (registro, login, catálogo). Las rutas internas nunca se exponen. */
public final class RutasPublicas {

    private static final Pattern PRODUCTO = Pattern.compile("^/api/productos(/[^/]+)?/?$");
    private static final Pattern INTERNA = Pattern.compile(".*/interno(/.*)?$|^/api/inventario/reservas(/.*)?$");

    private RutasPublicas() {
    }

    public static boolean esInterna(String path) {
        return INTERNA.matcher(path).matches();
    }

    public static boolean esPublica(HttpMethod metodo, String path) {
        if (HttpMethod.OPTIONS.equals(metodo)) {
            return true;
        }
        if (HttpMethod.POST.equals(metodo)) {
            return path.startsWith("/api/auth/");
        }
        if (!HttpMethod.GET.equals(metodo)) {
            return false;
        }
        return (PRODUCTO.matcher(path).matches() && !path.startsWith("/api/productos/gestion"))
                || path.startsWith("/api/categorias")
                || path.startsWith("/api/imagenes/")
                || path.equals("/api/inventario/disponibilidad")
                || path.equals("/api/tarifas-envio")
                || path.equals("/api/atencion/parametros");
    }
}
