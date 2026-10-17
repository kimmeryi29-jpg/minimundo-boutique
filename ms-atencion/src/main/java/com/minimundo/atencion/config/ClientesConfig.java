package com.minimundo.atencion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimundo.atencion.client.ServiceClient;
import com.minimundo.atencion.client.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ClientesConfig {

    private final Duration connect;
    private final Duration read;

    public ClientesConfig(@Value("${minimundo.http.connect-timeout-segundos:5}") long connect,
                          @Value("${minimundo.http.read-timeout-segundos:20}") long read) {
        this.connect = Duration.ofSeconds(connect);
        this.read = Duration.ofSeconds(read);
    }

    @Bean
    public ServiceClient pedidosClient(@Value("${minimundo.servicios.pedidos}") String url,
                                       ServiceTokenProvider tokens, ObjectMapper mapper) {
        return new ServiceClient("pedidos", url, tokens, mapper, connect, read);
    }

    @Bean
    public ServiceClient catalogoClient(@Value("${minimundo.servicios.catalogo}") String url,
                                        ServiceTokenProvider tokens, ObjectMapper mapper) {
        return new ServiceClient("catálogo", url, tokens, mapper, connect, read);
    }

    @Bean
    public ServiceClient logisticaClient(@Value("${minimundo.servicios.logistica}") String url,
                                         ServiceTokenProvider tokens, ObjectMapper mapper) {
        return new ServiceClient("logística", url, tokens, mapper, connect, read);
    }
}
