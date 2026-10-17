package com.minimundo.usuarios.service;

import com.minimundo.usuarios.dto.UsuarioRequests.CrearUsuarioRequest;
import com.minimundo.usuarios.error.ApiException;
import com.minimundo.usuarios.model.Rol;
import com.minimundo.usuarios.model.Usuario;
import com.minimundo.usuarios.repository.UsuarioRepository;
import com.minimundo.usuarios.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock UsuarioRepository repo;
    @Mock MongoTemplate mongo;
    @Mock AuditoriaService auditoria;
    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(repo, mongo, encoder, auditoria, Clock.systemUTC());
    }

    AuthUser admin = new AuthUser("a1", "admin@minimundo.co", "Admin", "ADMIN");

    @Test
    void hu8_creaRepartidorActivo() {
        when(repo.existsByEmail("jhon@minimundo.co")).thenReturn(false);
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.crearInterno(new CrearUsuarioRequest("Jhon", "jhon@minimundo.co", "clave12345",
                "REPARTIDOR", null), admin);

        assertThat(dto.rol()).isEqualTo("REPARTIDOR");
        assertThat(dto.activo()).isTrue();
    }

    @Test
    void hu8_rechazaRolNoPermitido() {
        assertThatThrownBy(() -> service.crearInterno(new CrearUsuarioRequest("X", "x@x.co", "clave12345",
                "CLIENTE", null), admin)).isInstanceOf(ApiException.class).hasMessageContaining("Rol no permitido");
        assertThatThrownBy(() -> service.crearInterno(new CrearUsuarioRequest("X", "x@x.co", "clave12345",
                "JEFE", null), admin)).isInstanceOf(ApiException.class).hasMessageContaining("Rol no permitido");
        verify(repo, never()).save(any());
    }

    @Test
    void hu11_desactivarConservaElRegistro() {
        Usuario u = new Usuario();
        u.setId("u2");
        u.setEmail("op@minimundo.co");
        u.setRol(Rol.OPERARIO);
        u.setActivo(true);
        when(repo.findById("u2")).thenReturn(Optional.of(u));
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.cambiarEstado("u2", false, admin);

        assertThat(dto.activo()).isFalse();
        verify(repo, never()).delete(any());
    }
}
