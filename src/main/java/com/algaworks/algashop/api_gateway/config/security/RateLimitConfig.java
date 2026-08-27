package com.algaworks.algashop.api_gateway.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

// =============================================================================
// RATE LIMIT — KEY RESOLVER DO GATEWAY: RateLimitConfig.java
// =============================================================================
//
// Expõe o bean "rateLimitKeyResolver", referenciado no application.yaml pela
// rota product-catalog-route via SpEL: key-resolver: "#{@rateLimitKeyResolver}".
//
// O RequestRateLimiter do Spring Cloud Gateway implementa um TOKEN BUCKET no
// Redis (replenishRate = reposição/seg, burstCapacity = pico). O bucket não é
// global: existe UM BUCKET POR CHAVE, e é esta classe que decide qual é a chave
// de cada requisição. Ou seja: o KeyResolver define a GRANULARIDADE do limite.
//
// ESTRATÉGIA DE CHAVE (por usuário autenticado):
//   1. Pega o Principal do exchange (reativo, Mono — nunca bloqueia).
//   2. Se for JwtAuthenticationToken (gateway é resource server), usa a claim
//      "sub" do JWT — cada usuário tem seu próprio bucket de 50 req/s.
//      Assim um cliente abusivo é limitado sem derrubar os demais.
//   3. Sem principal ou sem "sub" → chave fixa "anonymous": TODOS os não
//      autenticados COMPARTILHAM um único bucket (proteção mais agressiva).
//
// COMPORTAMENTO EM RUNTIME:
//   - Chave resolvida → Redis decide: tokens disponíveis? passa : HTTP 429.
//     Headers X-RateLimit-* voltam na resposta com o saldo.
//   - Se o KeyResolver retornasse Mono.empty(), o gateway NEGARIA a requisição
//     por padrão (deny-empty-key) — por isso o switchIfEmpty com fallback.
//   - Redis fora do ar → o filtro "falha aberto" (deixa passar sem limitar).
// =============================================================================

@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    private static final String DEFAULT_KEY = "anonymous";

    @Bean
    public KeyResolver rateLimitKeyResolver() {
        return exchange -> exchange.getPrincipal().map(principal -> {
                if (principal instanceof JwtAuthenticationToken jwtToken) {
                    String sub = jwtToken.getToken().getClaimAsString("sub");
                    return sub != null ? sub : DEFAULT_KEY;
                }

                return DEFAULT_KEY;
            }).switchIfEmpty(Mono.just(DEFAULT_KEY))
              // debug, nao info: e uma linha POR REQUISICAO carregando o sub do usuario
              .doOnNext(key -> log.debug("Rate limit key: {}", key));
        }
}
