package com.minimundo.usuarios.config;

import com.minimundo.usuarios.model.Direccion;
import com.minimundo.usuarios.model.Rol;
import com.minimundo.usuarios.model.Usuario;
import com.minimundo.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Crea el administrador inicial y, si DEMO_DATA=true, un usuario de cada rol para la demostración.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean demo;
    private final String demoPassword;

    public DataSeeder(UsuarioRepository repo, PasswordEncoder encoder,
                      @Value("${minimundo.admin.email:admin@minimundo.co}") String adminEmail,
                      @Value("${minimundo.admin.password:Admin12345}") String adminPassword,
                      @Value("${minimundo.demo-data:false}") boolean demo,
                      @Value("${minimundo.demo-password:Demo12345}") String demoPassword) {
        this.repo = repo;
        this.encoder = encoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.demo = demo;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!repo.existsByRol(Rol.ADMIN)) {
            crear("Administrador MiniMundo", adminEmail, adminPassword, Rol.ADMIN, null);
            log.info("Administrador inicial creado: {}", adminEmail);
        }
        if (demo) {
            crear("Laura Gómez (cliente demo)", "cliente@minimundo.co", demoPassword, Rol.CLIENTE,
                    new Direccion("Calle 45 # 27-30 Apto 502", "Cabecera", "Bucaramanga", "3001234567",
                            "Portería principal"));
            crear("Andrés Pérez (asesor demo)", "asesor@minimundo.co", demoPassword, Rol.ASESOR, null);
            crear("Camila Rojas (operaria demo)", "operario@minimundo.co", demoPassword, Rol.OPERARIO, null);
            crear("Jhon Díaz (repartidor demo)", "repartidor@minimundo.co", demoPassword, Rol.REPARTIDOR, null);
            crear("Sergio Ruiz (repartidor demo)", "repartidor2@minimundo.co", demoPassword, Rol.REPARTIDOR, null);
        }
    }

    private void crear(String nombre, String email, String password, Rol rol, Direccion direccion) {
        String normalizado = email.trim().toLowerCase();
        if (repo.existsByEmail(normalizado)) {
            return;
        }
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setEmail(normalizado);
        u.setPasswordHash(encoder.encode(password));
        u.setRol(rol);
        u.setActivo(true);
        u.setDireccion(direccion);
        u.setTelefono(direccion != null ? direccion.getTelefono() : null);
        u.setFechaCreacion(Instant.now());
        repo.save(u);
    }
}
