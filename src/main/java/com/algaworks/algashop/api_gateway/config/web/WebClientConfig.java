package com.algaworks.algashop.api_gateway.config.web;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.web.reactive.function.client.ServerBearerExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuração do WebClient usado nas chamadas aos microsserviços downstream.
 *
 * Expõe um WebClient.Builder anotado com @LoadBalanced, permitindo resolver
 * endereços lb://{service-id} pelo service discovery (Eureka) com balanceamento
 * de carga no lado do cliente.
 *
 * O bean WebClient é construído com o ServerBearerExchangeFilterFunction, filtro
 * que propaga automaticamente o access token (Bearer) da requisição original
 * para as chamadas aos serviços internos — essencial para o API Composition
 * feito pelo gateway manter o contexto de segurança.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        ServerBearerExchangeFilterFunction filterFunction = new ServerBearerExchangeFilterFunction();
        return builder
                .filter(filterFunction)
                .build();
    }
}
