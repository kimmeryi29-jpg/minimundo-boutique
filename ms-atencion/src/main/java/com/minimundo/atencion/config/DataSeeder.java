package com.minimundo.atencion.config;

import com.minimundo.atencion.model.Parametros;
import com.minimundo.atencion.repository.ParametrosRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Parámetros iniciales: 30 días para cambios y devoluciones y 5 días hábiles de vencimiento (HU-84). */
@Component
public class DataSeeder implements ApplicationRunner {

    private final ParametrosRepository parametros;

    public DataSeeder(ParametrosRepository parametros) {
        this.parametros = parametros;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!parametros.existsById(Parametros.ID)) {
            parametros.save(new Parametros());
        }
    }
}
