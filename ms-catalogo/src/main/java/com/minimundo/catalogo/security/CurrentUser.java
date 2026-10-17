package com.minimundo.catalogo.security;

import com.minimundo.catalogo.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Acceso al usuario autenticado de la petición actual.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthUser> opcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthUser get() {
        return opcional().orElseThrow(() -> ApiException.unauthorized("Debes iniciar sesión"));
    }
}
