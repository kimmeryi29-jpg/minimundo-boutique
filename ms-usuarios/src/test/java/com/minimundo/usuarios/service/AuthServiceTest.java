package com.minimundo.usuarios.service;

import com.minimundo.usuarios.dto.AuthDtos.LoginRequest;
import com.minimundo.usuarios.dto.AuthDtos.RegistroRequest;
import com.minimundo.usuarios.error.ApiException;
import com.minimundo.usuarios.model.Rol;
import com.minimundo.usuarios.model.Usuario;
import com.minimundo.usuarios.repository.UsuarioRepository;
import com.minimundo.usuarios.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UsuarioRepository repo;
    @Mock AuditoriaService auditoria;
    @Mock MailService mail;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    JwtService jwt = new JwtService("secreto-de-pruebas", 60, "ms-usuarios");
    Clock clock = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(repo, encoder, jwt, auditoria, mail, clock, 5, 15, "http://localhost:8080");
    }

    private Usuario usuario(String password) {
        Usuario u = new Usuario();
        u.setId("u1");
        u.setNombre("Laura");
        u.setEmail("laura@correo.com");
        u.setPasswordHash(encoder.encode(password));
        u.setRol(Rol.CLIENTE);
        u.setActivo(true);
        return u;
    }

    @Test
    void hu1_registroExitosoCreaClienteConContrasenaCifrada() {
        when(repo.existsByEmail("laura@correo.com")).thenReturn(false);
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.registrar(new RegistroRequest("Laura", "Laura@Correo.com", "clave12345"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).save(captor.capture());
        assertThat(dto.rol()).isEqualTo("CLIENTE");
        assertThat(captor.getValue().getEmail()).isEqualTo("laura@correo.com");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("clave12345").startsWith("$2");
    }

    @Test
    void hu7_rechazaCorreoRegistradoSinImportarMayusculas() {
        when(repo.existsByEmail("laura@correo.com")).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(new RegistroRequest("Laura", "LAURA@correo.com", "clave12345")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessage("El correo ya está en uso");
        verify(repo, never()).save(any());
    }

    @Test
    void hu2_loginCorrectoDevuelveTokenConRol() {
        when(repo.findByEmail("laura@correo.com")).thenReturn(Optional.of(usuario("clave12345")));

        var resp = service.login(new LoginRequest("laura@correo.com", "clave12345"));

        assertThat(resp.token()).isNotBlank();
        assertThat(jwt.validar(resp.token())).get().extracting("rol").isEqualTo("CLIENTE");
    }

    @Test
    void hu3_credencialesIncorrectasNoRevelanCualDatoFallo() {
        when(repo.findByEmail("laura@correo.com")).thenReturn(Optional.of(usuario("clave12345")));
        when(repo.findByEmail("otro@correo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("laura@correo.com", "mala")))
                .hasMessage(AuthService.CREDENCIALES_INVALIDAS);
        assertThatThrownBy(() -> service.login(new LoginRequest("otro@correo.com", "clave12345")))
                .hasMessage(AuthService.CREDENCIALES_INVALIDAS);
    }

    @Test
    void hu13_bloqueaLaCuentaAlQuintoIntentoFallido() {
        Usuario u = usuario("clave12345");
        u.setIntentosFallidos(4);
        when(repo.findByEmail("laura@correo.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("laura@correo.com", "mala")))
                .hasMessageContaining("bloqueada");
        assertThat(u.getBloqueadoHasta()).isEqualTo(Instant.now(clock).plus(15, ChronoUnit.MINUTES));
    }

    @Test
    void hu13_cuentaBloqueadaRechazaInclusoConClaveCorrecta() {
        Usuario u = usuario("clave12345");
        u.setBloqueadoHasta(Instant.now(clock).plus(10, ChronoUnit.MINUTES));
        when(repo.findByEmail("laura@correo.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("laura@correo.com", "clave12345")))
                .hasMessageContaining("bloqueada temporalmente");
    }

    @Test
    void hu13_loginExitosoReiniciaElContador() {
        Usuario u = usuario("clave12345");
        u.setIntentosFallidos(3);
        when(repo.findByEmail("laura@correo.com")).thenReturn(Optional.of(u));

        service.login(new LoginRequest("laura@correo.com", "clave12345"));

        assertThat(u.getIntentosFallidos()).isZero();
    }

    @Test
    void hu11_usuarioInactivoNoPuedeIniciarSesion() {
        Usuario u = usuario("clave12345");
        u.setActivo(false);
        when(repo.findByEmail("laura@correo.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("laura@correo.com", "clave12345")))
                .hasMessageContaining("desactivada");
    }

    @Test
    void hu6_enlaceVencidoSeRechaza() {
        Usuario u = usuario("clave12345");
        u.setResetTokenHash(AuthService.sha256("tok"));
        u.setResetTokenExpira(Instant.now(clock).minus(1, ChronoUnit.MINUTES));
        when(repo.findByResetTokenHash(AuthService.sha256("tok"))).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.restablecer("tok", "nuevaClave1"))
                .hasMessageContaining("Solicita uno nuevo");
    }

    @Test
    void hu6_enlaceVigenteActualizaLaContrasenaYSeInvalida() {
        Usuario u = usuario("clave12345");
        u.setResetTokenHash(AuthService.sha256("tok"));
        u.setResetTokenExpira(Instant.now(clock).plus(10, ChronoUnit.MINUTES));
        when(repo.findByResetTokenHash(AuthService.sha256("tok"))).thenReturn(Optional.of(u));

        service.restablecer("tok", "nuevaClave1");

        assertThat(encoder.matches("nuevaClave1", u.getPasswordHash())).isTrue();
        assertThat(u.getResetTokenHash()).isNull();
    }
}
