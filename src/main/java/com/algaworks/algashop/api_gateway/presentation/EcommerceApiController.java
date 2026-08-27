package com.algaworks.algashop.api_gateway.presentation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller que implementa o API Composition Pattern no papel de BFF (Backend For Frontend).
 *
 * Agrega em uma única resposta os dados da home do e-commerce, chamando o serviço
 * product-catalog via WebClient com load balancing (lb://) para buscar produtos em
 * destaque (com desconto) e categorias em paralelo (Mono.zip), evitando que o
 * frontend precise fazer múltiplas requisições.
 *
 * O acesso exige os scopes OAuth2 products:read e categories:read, e o token da
 * requisição original é propagado às chamadas downstream pelo WebClient
 *
 * O retorno usa Mono, o publisher reativo do Project
 * Reactor que emite no máximo um valor de forma assíncrona e não bloqueante: as duas
 * chamadas HTTP são apenas declaradas e só executam quando o WebFlux assina o Mono
 * resultante, sem prender thread enquanto espera as respostas. O Mono.zip combina os
 * dois resultados quando ambos chegam, e o map monta a resposta final direto em Map,
 * dispensando a criação de DTOs.
 */
@RestController
@RequestMapping("/api/v1/ecommerce/home")
public class EcommerceApiController {

    private final WebClient webClient;

    public EcommerceApiController(WebClient webClient) {
        this.webClient = webClient;
    }

    // interface assincronas que produz um valor ou nenhum, utilizamos o map para evitar dto
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_products:read') and hasAuthority('SCOPE_categories:read')")
    public Mono<Map<String, Object>> getHome() {
        Mono<Map> productList = webClient.get().uri("lb://product-catalog/api/v1/products?hasDiscount=true")
                .retrieve()
                .bodyToMono(Map.class);

        Mono<Map> categoriesList = webClient.get().uri("lb://product-catalog/api/v1/categories")
                .retrieve()
                .bodyToMono(Map.class);

        return Mono.zip(productList, categoriesList)
                .map(tuple -> Map.of(
                     "highlights", tuple.getT1().get("content"),
                     "categories", tuple.getT2().get("content")
                ));
    }
}
