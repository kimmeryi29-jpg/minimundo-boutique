package com.minimundo.usuarios.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Registro inmutable de inicios de sesión y cambios de rol (HU-15). */
@Document("auditoria")
public class EventoAuditoria {

    @Id
    private String id;
    private TipoEvento tipo;
    @Indexed
    private String email;
    private String usuarioId;
    private String resultado;
    private String detalle;
    private String actor;
    @Indexed
    private Instant fecha;

    public EventoAuditoria() {
    }

    public EventoAuditoria(TipoEvento tipo, String email, String usuarioId, String resultado, String detalle,
                           String actor) {
        this.tipo = tipo;
        this.email = email;
        this.usuarioId = usuarioId;
        this.resultado = resultado;
        this.detalle = detalle;
        this.actor = actor;
        this.fecha = Instant.now();
    }

    public String getId() { return id; }
    public TipoEvento getTipo() { return tipo; }
    public String getEmail() { return email; }
    public String getUsuarioId() { return usuarioId; }
    public String getResultado() { return resultado; }
    public String getDetalle() { return detalle; }
    public String getActor() { return actor; }
    public Instant getFecha() { return fecha; }
}
