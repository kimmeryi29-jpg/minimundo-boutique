package com.minimundo.usuarios.repository;

import com.minimundo.usuarios.model.Rol;
import com.minimundo.usuarios.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRol(Rol rol);

    List<Usuario> findByRolAndActivoOrderByNombreAsc(Rol rol, boolean activo);

    Optional<Usuario> findByResetTokenHash(String resetTokenHash);
}
