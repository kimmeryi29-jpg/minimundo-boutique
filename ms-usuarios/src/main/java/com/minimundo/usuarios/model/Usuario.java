package com.minimundo.usuarios.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Documento de la colección usuarios (db_usuarios). */
@Document("usuarios")
public class Usuario {

    @Id
    private String id;
    private String nombre;
    /** Siempre en minúsculas: un correo con mayúsculas distintas es el mismo correo (HU-7). */
    @Indexed(unique = true)
    private String email;
    /** Hash BCrypt, nunca la contraseña en texto plano (HU-12). */
    private String passwordHash;
    @Indexed
    private Rol rol;
    private boolean activo = true;
    private String telefono;
    private Direccion direccion;
    private int intentosFallidos;
    private Instant bloqueadoHasta;
    private String resetTokenHash;
    private Instant resetTokenExpira;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Instant ultimoAcceso;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Direccion getDireccion() { return direccion; }
    public void setDireccion(Direccion direccion) { this.direccion = direccion; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }
    public Instant getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(Instant bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }
    public String getResetTokenHash() { return resetTokenHash; }
    public void setResetTokenHash(String resetTokenHash) { this.resetTokenHash = resetTokenHash; }
    public Instant getResetTokenExpira() { return resetTokenExpira; }
    public void setResetTokenExpira(Instant resetTokenExpira) { this.resetTokenExpira = resetTokenExpira; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(Instant fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
    public Instant getUltimoAcceso() { return ultimoAcceso; }
    public void setUltimoAcceso(Instant ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; }
}
