package com.minimundo.atencion.service;

import com.minimundo.atencion.error.ApiException;
import com.minimundo.atencion.model.Parametros;
import com.minimundo.atencion.repository.ParametrosRepository;
import org.springframework.stereotype.Service;

/** HU-83 y HU-84. El nuevo valor se aplica a partir de su cambio, sin volver a desplegar. */
@Service
public class ParametrosService {

    private final ParametrosRepository repo;

    public ParametrosService(ParametrosRepository repo) {
        this.repo = repo;
    }

    public Parametros obtener() {
        return repo.findById(Parametros.ID).orElseGet(Parametros::new);
    }

    public Parametros actualizar(Integer plazo, Integer vencimiento) {
        if (plazo == null || plazo <= 0) {
            throw ApiException.badRequest("El plazo para cambios y devoluciones debe ser un entero mayor que cero");
        }
        if (vencimiento == null || vencimiento <= 0) {
            throw ApiException.badRequest("Los días hábiles de vencimiento deben ser un entero mayor que cero");
        }
        Parametros p = obtener();
        p.setPlazoCambiosDias(plazo);
        p.setDiasVencimientoPqrs(vencimiento);
        return repo.save(p);
    }
}
