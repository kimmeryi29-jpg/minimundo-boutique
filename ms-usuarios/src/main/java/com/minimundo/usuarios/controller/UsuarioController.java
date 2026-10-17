package com.minimundo.usuarios.controller;

import com.minimundo.usuarios.dto.UsuarioDto;
import com.minimundo.usuarios.dto.UsuarioRequests.ActualizarUsuarioRequest;
import com.minimundo.usuarios.dto.UsuarioRequests.CrearUsuarioRequest;
import com.minimundo.usuarios.dto.UsuarioRequests.PerfilRequest;
import com.minimundo.usuarios.security.CurrentUser;
import com.minimundo.usuarios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Perfil del cliente y gestión de usuarios internos")
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/me")
    @Operation(summary = "HU-5 · Consultar mi perfil")
    public UsuarioDto miPerfil() {
        return UsuarioDto.de(servicio.obtener(CurrentUser.get().id()));
    }

    @PutMapping("/me")
    @Operation(summary = "HU-5 · Actualizar mis datos personales y mi dirección de envío")
    @ApiResponse(responseCode = "400", description = "Campo obligatorio vacío")
    public UsuarioDto actualizarPerfil(@Valid @RequestBody PerfilRequest req) {
        return servicio.actualizarPerfil(CurrentUser.get().id(), req);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-9 · Listar usuarios filtrando por rol y estado")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    public List<UsuarioDto> listar(@RequestParam(required = false) String rol,
                                   @RequestParam(required = false) Boolean activo,
                                   @RequestParam(required = false) String q) {
        return servicio.listar(rol, activo, q);
    }

    @GetMapping("/clientes")
    @PreAuthorize("hasAnyRole('ASESOR','ADMIN')")
    @Operation(summary = "HU-71 · Buscar clientes por nombre o correo")
    public List<UsuarioDto> buscarClientes(@RequestParam(required = false) String q) {
        return servicio.buscarClientes(q);
    }

    @GetMapping("/repartidores")
    @PreAuthorize("hasAnyRole('OPERARIO','ADMIN','SERVICIO')")
    @Operation(summary = "HU-106 · Repartidores activos disponibles para asignar")
    public List<UsuarioDto> repartidores() {
        return servicio.repartidoresActivos();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ASESOR','SERVICIO')")
    @Operation(summary = "Consultar un usuario por su identificador")
    public UsuarioDto obtener(@PathVariable String id) {
        return UsuarioDto.de(servicio.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "HU-8 · Crear un usuario interno (ASESOR, OPERARIO, REPARTIDOR o ADMIN)")
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol ADMIN")
    @ApiResponse(responseCode = "409", description = "El correo ya está en uso")
    public UsuarioDto crear(@Valid @RequestBody CrearUsuarioRequest req) {
        return servicio.crearInterno(req, CurrentUser.get());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-10 · Actualizar los datos y el rol de un usuario")
    public UsuarioDto actualizar(@PathVariable String id, @Valid @RequestBody ActualizarUsuarioRequest req) {
        return servicio.actualizar(id, req, CurrentUser.get());
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-11 · Desactivar un usuario sin borrar su historial")
    public UsuarioDto desactivar(@PathVariable String id) {
        return servicio.cambiarEstado(id, false, CurrentUser.get());
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Volver a activar un usuario")
    public UsuarioDto activar(@PathVariable String id) {
        return servicio.cambiarEstado(id, true, CurrentUser.get());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "HU-11 · Eliminar lógicamente (desactiva y conserva el historial)")
    public UsuarioDto eliminar(@PathVariable String id) {
        return servicio.cambiarEstado(id, false, CurrentUser.get());
    }
}
