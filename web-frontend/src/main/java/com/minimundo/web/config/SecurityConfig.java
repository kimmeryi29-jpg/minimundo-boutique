package com.minimundo.web.config;

import com.minimundo.web.api.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;

/**
 * HU-14: cada rol solo accede a sus vistas (403 en caso contrario). HU-2: sin sesión, una vista protegida
 * redirige al formulario de inicio de sesión. HU-4: cerrar sesión invalida la sesión del servidor.
 */
@Configuration
public class SecurityConfig {

    public static final String LOGIN_ERROR = "LOGIN_ERROR";
    public static final String LOGIN_EMAIL = "LOGIN_EMAIL";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayAuthenticationProvider provider) throws Exception {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        http.authenticationProvider(provider)
                .requestCache(r -> r.requestCache(cache))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/catalogo", "/producto/**", "/img/**", "/css/**", "/js/**",
                                "/static/**", "/webjars/**", "/favicon.ico", "/login", "/registro", "/recuperar",
                                "/restablecer", "/error", "/403", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/carrito/**", "/checkout/**", "/cuenta/**").hasRole("CLIENTE")
                        .requestMatchers("/asesor/**").hasRole("ASESOR")
                        .requestMatchers("/operario/**").hasRole("OPERARIO")
                        .requestMatchers("/repartidor/**").hasRole("REPARTIDOR")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").loginProcessingUrl("/login")
                        .usernameParameter("email").passwordParameter("password")
                        .successHandler(exito(cache)).failureHandler(fallo()).permitAll())
                .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/?salida").invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID").clearAuthentication(true))
                .exceptionHandling(e -> e.accessDeniedPage("/403"))
                .sessionManagement(s -> s.sessionFixation(f -> f.migrateSession()));
        return http.build();
    }

    /** HU-2: tras iniciar sesión, cada rol llega a su vista de inicio (o a la página que intentaba abrir). */
    private AuthenticationSuccessHandler exito(HttpSessionRequestCache cache) {
        return (HttpServletRequest req, HttpServletResponse res, Authentication auth) -> {
            req.getSession().removeAttribute(LOGIN_ERROR);
            req.getSession().removeAttribute(LOGIN_EMAIL);
            SessionUser u = (SessionUser) auth.getPrincipal();
            SavedRequest saved = cache.getRequest(req, res);
            String destino = u.inicio();
            if (saved != null && "CLIENTE".equals(u.rol()) && !saved.getRedirectUrl().contains("/login")) {
                destino = saved.getRedirectUrl();
            }
            cache.removeRequest(req, res);
            res.sendRedirect(destino.startsWith("http") ? destino : req.getContextPath() + destino);
        };
    }

    /** HU-3: muestra el mensaje del servicio y conserva el correo escrito. */
    private AuthenticationFailureHandler fallo() {
        return (HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) -> {
            req.getSession().setAttribute(LOGIN_ERROR, ex.getMessage());
            req.getSession().setAttribute(LOGIN_EMAIL, req.getParameter("email"));
            try {
                res.sendRedirect(req.getContextPath() + "/login?error");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
