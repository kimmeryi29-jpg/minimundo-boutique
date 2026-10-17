package com.minimundo.usuarios.model;

/** Eventos de seguridad que se registran para la auditoría (HU-15). */
public enum TipoEvento {
    LOGIN_EXITOSO, LOGIN_FALLIDO, CUENTA_BLOQUEADA, CAMBIO_ROL, USUARIO_CREADO, USUARIO_DESACTIVADO,
    USUARIO_ACTIVADO, RECUPERACION_SOLICITADA, CONTRASENA_RESTABLECIDA
}
