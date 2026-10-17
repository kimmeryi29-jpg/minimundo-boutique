package com.minimundo.usuarios.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío del enlace de recuperación (HU-6). Si no hay servidor SMTP configurado (MAIL_HOST),
 * el servicio trabaja en modo demostración y devuelve el enlace para mostrarlo en pantalla.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String host;
    private final String remitente;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${minimundo.mail.remitente:no-responder@minimundo.co}") String remitente) {
        this.senderProvider = senderProvider;
        this.host = host;
        this.remitente = remitente;
    }

    public boolean configurado() {
        return host != null && !host.isBlank() && senderProvider.getIfAvailable() != null;
    }

    /** @return true si el correo se envió; false si el servicio está en modo demostración. */
    public boolean enviarRecuperacion(String para, String nombre, String enlace) {
        if (!configurado()) {
            log.info("[MODO DEMOSTRACIÓN] Enlace de recuperación para {}: {}", para, enlace);
            return false;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(remitente);
        msg.setTo(para);
        msg.setSubject("MiniMundo Boutique · Recupera tu contraseña");
        msg.setText("Hola " + nombre + ",\n\n"
                + "Recibimos una solicitud para restablecer tu contraseña. Abre este enlace (válido por 30 minutos):\n\n"
                + enlace + "\n\n"
                + "Si no fuiste tú, ignora este mensaje.\n\nMiniMundo Boutique");
        senderProvider.getObject().send(msg);
        return true;
    }
}
