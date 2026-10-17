package com.minimundo.web.config;

import com.minimundo.web.api.Api;
import com.minimundo.web.api.ApiException;
import com.minimundo.web.api.SessionUser;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** HU-2: el login se valida contra ms-usuarios a través del gateway; el JWT queda en la sesión del servidor. */
@Component
public class GatewayAuthenticationProvider implements AuthenticationProvider {

    private final Api api;

    public GatewayAuthenticationProvider(Api api) {
        this.api = api;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Authentication authenticate(Authentication authentication) {
        String email = authentication.getName() == null ? "" : authentication.getName().trim();
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        if (email.isEmpty() || password.isEmpty()) {
            throw new BadCredentialsException("Escribe tu correo y tu contraseña");
        }
        try {
            Map<String, Object> r = api.login(email, password);
            Map<String, Object> u = (Map<String, Object>) r.get("usuario");
            SessionUser user = new SessionUser(String.valueOf(u.get("id")), String.valueOf(u.get("nombre")),
                    String.valueOf(u.get("email")), String.valueOf(u.get("rol")), String.valueOf(r.get("token")));
            return new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.rol())));
        } catch (ApiException e) {
            if (e.noDisponible()) {
                throw new AuthenticationServiceException(e.getMessage());
            }
            throw new BadCredentialsException(e.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
