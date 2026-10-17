package com.minimundo.pedidos.config;

import com.minimundo.pedidos.model.TarifaEnvio;
import com.minimundo.pedidos.repository.TarifaRepository;
import com.minimundo.pedidos.service.Textos;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tarifas de envío iniciales (el administrador las puede cambiar desde la pantalla de parámetros). */
@Component
public class DataSeeder implements ApplicationRunner {

    private final TarifaRepository tarifas;

    public DataSeeder(TarifaRepository tarifas) {
        this.tarifas = tarifas;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tarifas.count() > 0) {
            return;
        }
        Map<String, Long> iniciales = new LinkedHashMap<>();
        iniciales.put("Bucaramanga", 8000L);
        iniciales.put("Floridablanca", 9000L);
        iniciales.put("Girón", 10000L);
        iniciales.put("Piedecuesta", 10000L);
        iniciales.put("Bogotá", 15000L);
        iniciales.put("Medellín", 15000L);
        iniciales.put("Cali", 16000L);
        iniciales.put("Barranquilla", 18000L);
        iniciales.put("Cúcuta", 14000L);
        iniciales.forEach((ciudad, costo) -> {
            TarifaEnvio t = new TarifaEnvio();
            t.setCiudad(ciudad);
            t.setCiudadNormalizada(Textos.normalizar(ciudad));
            t.setCosto(costo);
            tarifas.save(t);
        });
    }
}
