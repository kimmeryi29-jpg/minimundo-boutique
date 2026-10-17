package com.minimundo.atencion.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Cuenta días hábiles (lunes a viernes) transcurridos desde una fecha (HU-74). */
public final class DiasHabiles {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private DiasHabiles() {
    }

    public static long entre(Instant desde, Instant hasta) {
        LocalDate d = desde.atZone(ZONA).toLocalDate();
        LocalDate h = hasta.atZone(ZONA).toLocalDate();
        long dias = 0;
        LocalDate cursor = d.plusDays(1);
        while (!cursor.isAfter(h)) {
            DayOfWeek dw = cursor.getDayOfWeek();
            if (dw != DayOfWeek.SATURDAY && dw != DayOfWeek.SUNDAY) {
                dias++;
            }
            cursor = cursor.plusDays(1);
        }
        return dias;
    }
}
