package com.minimundo.usuarios.service;

import com.minimundo.usuarios.dto.UsuarioDto;
import com.minimundo.usuarios.dto.UsuarioRequests.ActualizarUsuarioRequest;
import com.minimundo.usuarios.dto.UsuarioRequests.CrearUsuarioRequest;
import com.minimundo.usuarios.dto.UsuarioRequests.PerfilRequest;
import com.minimundo.usuarios.error.ApiException;
import com.minimundo.usuarios.model.Direccion;
import com.minimundo.usuarios.model.Rol;
import com.minimundo.usuarios.model.TipoEvento;
import com.minimundo.usuarios.model.Usuario;
import com.minimundo.usuarios.repository.UsuarioRepository;
import com.minimundo.usuarios.security.AuthUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Gestión de usuarios: CRUD de usuarios internos (HU-8 a HU-11), perfil del cliente (HU-5),
 * búsqueda de clientes para el asesor (HU-71) y repartidores disponibles (HU-106).
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repo;
    private final MongoTemplate mongo;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public UsuarioService(UsuarioRepository repo, MongoTemplate mongo, PasswordEncoder encoder,
                          AuditoriaService auditoria, Clock clock) {
        this.repo = repo;
        this.mongo = mongo;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public Usuario obtener(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("El usuario no existe"));
    }

    /** HU-9: listado filtrable por rol, estado y texto. */
    public List<UsuarioDto> listar(String rol, Boolean activo, String texto) {
        Query q = new Query();
        if (rol != null && !rol.isBlank()) {
            q.addCriteria(Criteria.where("rol").is(parseRol(rol)));
        }
        if (activo != null) {
            q.addCriteria(Criteria.where("activo").is(activo));
        }
        if (texto != null && !texto.isBlank()) {
            String patron = Pattern.quote(texto.trim());
            q.addCriteria(new Criteria().orOperator(
                    Criteria.where("nombre").regex(patron, "i"),
                    Criteria.where("email").regex(patron, "i")));
        }
        q.with(Sort.by("nombre"));
        return mongo.find(q, Usuario.class).stream().map(UsuarioDto::de).toList();
    }

    /** HU-71: el asesor busca clientes por nombre o correo. */
    public List<UsuarioDto> buscarClientes(String texto) {
        return listar(Rol.CLIENTE.name(), null, texto);
    }

    /** HU-106: solo usuarios activos con rol REPARTIDOR. */
    public List<UsuarioDto> repartidoresActivos() {
        return repo.findByRolAndActivoOrderByNombreAsc(Rol.REPARTIDOR, true).stream().map(UsuarioDto::de).toList();
    }

    /** HU-8: crea un usuario interno (asesor, operario, repartidor o administrador). */
    public UsuarioDto crearInterno(CrearUsuarioRequest req, AuthUser actor) {
        Rol rol = parseRol(req.rol());
        if (!rol.esInterno()) {
            throw ApiException.badRequest("Rol no permitido: los usuarios internos pueden ser ASESOR, OPERARIO, "
                    + "REPARTIDOR o ADMIN. Los clientes se registran desde la tienda.");
        }
        String email = AuthService.normalizarEmail(req.email());
        if (repo.existsByEmail(email)) {
            throw ApiException.conflict("El correo ya está en uso");
        }
        Usuario u = new Usuario();
        u.setNombre(req.nombre().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRol(rol);
        u.setTelefono(req.telefono());
        u.setActivo(true);
        u.setFechaCreacion(Instant.now(clock));
        Usuario guardado = repo.save(u);
        auditoria.registrar(TipoEvento.USUARIO_CREADO, email, guardado.getId(), "EXITOSO", "Rol " + rol,
                actor.email());
        return UsuarioDto.de(guardado);
    }

    /** HU-10: los nuevos permisos aplican desde el siguiente inicio de sesión (el JWT vigente conserva el rol). */
    public UsuarioDto actualizar(String id, ActualizarUsuarioRequest req, AuthUser actor) {
        Usuario u = obtener(id);
        Rol nuevoRol = parseRol(req.rol());
        if (u.getRol() == Rol.CLIENTE && nuevoRol != Rol.CLIENTE) {
            throw ApiException.badRequest("Un cliente no puede convertirse en usuario interno desde aquí");
        }
        if (u.getRol() != Rol.CLIENTE && nuevoRol == Rol.CLIENTE) {
            throw ApiException.badRequest("Rol no permitido para un usuario interno");
        }
        String email = AuthService.normalizarEmail(req.email());
        if (!email.equals(u.getEmail()) && repo.existsByEmail(email)) {
            throw ApiException.conflict("El correo ya está en uso");
        }
        if (u.getId().equals(actor.id()) && nuevoRol != Rol.ADMIN) {
            throw ApiException.badRequest("No puedes quitarte a ti mismo el rol de administrador");
        }
        Rol anterior = u.getRol();
        u.setNombre(req.nombre().trim());
        u.setEmail(email);
        u.setTelefono(req.telefono());
        u.setRol(nuevoRol);
        u.setFechaActualizacion(Instant.now(clock));
        Usuario guardado = repo.save(u);
        if (anterior != nuevoRol) {
            auditoria.registrar(TipoEvento.CAMBIO_ROL, u.getEmail(), u.getId(), "EXITOSO",
                    "Cambio de rol " + anterior + " → " + nuevoRol, actor.email());
        }
        return UsuarioDto.de(guardado);
    }

    /** HU-11: desactivar sin borrar ningún registro. */
    public UsuarioDto cambiarEstado(String id, boolean activo, AuthUser actor) {
        Usuario u = obtener(id);
        if (!activo && u.getId().equals(actor.id())) {
            throw ApiException.badRequest("No puedes desactivar tu propia cuenta");
        }
        u.setActivo(activo);
        u.setFechaActualizacion(Instant.now(clock));
        Usuario guardado = repo.save(u);
        auditoria.registrar(activo ? TipoEvento.USUARIO_ACTIVADO : TipoEvento.USUARIO_DESACTIVADO, u.getEmail(),
                u.getId(), "EXITOSO", activo ? "Usuario activado" : "Usuario desactivado", actor.email());
        return UsuarioDto.de(guardado);
    }

    /** HU-5: el cliente consulta y actualiza sus datos y su dirección de envío. */
    public UsuarioDto actualizarPerfil(String id, PerfilRequest req) {
        Usuario u = obtener(id);
        u.setNombre(req.nombre().trim());
        u.setTelefono(req.telefono());
        if (req.direccion() != null) {
            var d = req.direccion();
            u.setDireccion(new Direccion(d.direccion().trim(), d.barrio(), d.ciudad().trim(), d.telefono().trim(),
                    d.indicaciones()));
        }
        u.setFechaActualizacion(Instant.now(clock));
        return UsuarioDto.de(repo.save(u));
    }

    static Rol parseRol(String rol) {
        try {
            return Rol.valueOf(rol.trim().toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("Rol no permitido: " + rol);
        }
    }
}
