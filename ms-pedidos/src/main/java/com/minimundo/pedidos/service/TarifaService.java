package com.minimundo.pedidos.service;

import com.minimundo.pedidos.dto.PedidoDtos.TarifaRequest;
import com.minimundo.pedidos.error.ApiException;
import com.minimundo.pedidos.model.TarifaEnvio;
import com.minimundo.pedidos.repository.TarifaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** HU-38 y HU-81: costo de envío por ciudad, editable por el administrador sin volver a desplegar. */
@Service
public class TarifaService {

    private final TarifaRepository repo;

    public TarifaService(TarifaRepository repo) {
        this.repo = repo;
    }

    public List<TarifaEnvio> listar() {
        return repo.findAllByOrderByCiudadAsc();
    }

    public Optional<TarifaEnvio> porCiudad(String ciudad) {
        if (ciudad == null || ciudad.isBlank()) {
            return Optional.empty();
        }
        return repo.findByCiudadNormalizada(Textos.normalizar(ciudad));
    }

    public TarifaEnvio crear(TarifaRequest req) {
        validar(req);
        String norm = Textos.normalizar(req.ciudad());
        if (repo.existsByCiudadNormalizada(norm)) {
            throw ApiException.conflict("Ya existe una tarifa para " + req.ciudad().trim());
        }
        TarifaEnvio t = new TarifaEnvio();
        t.setCiudad(Textos.capitalizarPalabras(req.ciudad()));
        t.setCiudadNormalizada(norm);
        t.setCosto(req.costo());
        return repo.save(t);
    }

    public TarifaEnvio actualizar(String id, TarifaRequest req) {
        validar(req);
        TarifaEnvio t = repo.findById(id).orElseThrow(() -> ApiException.notFound("La tarifa no existe"));
        String norm = Textos.normalizar(req.ciudad());
        if (!norm.equals(t.getCiudadNormalizada()) && repo.existsByCiudadNormalizada(norm)) {
            throw ApiException.conflict("Ya existe una tarifa para " + req.ciudad().trim());
        }
        t.setCiudad(Textos.capitalizarPalabras(req.ciudad()));
        t.setCiudadNormalizada(norm);
        t.setCosto(req.costo());
        return repo.save(t);
    }

    public void eliminar(String id) {
        if (!repo.existsById(id)) {
            throw ApiException.notFound("La tarifa no existe");
        }
        repo.deleteById(id);
    }

    /** HU-81: valor negativo o no numérico se rechaza. */
    static void validar(TarifaRequest req) {
        if (req.costo() == null || req.costo() < 0) {
            throw ApiException.badRequest("El costo de envío debe ser un valor mayor o igual a cero");
        }
    }
}
