package com.user.management.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LangGraphKeepAlive {

    @Value("${fastapi.base-url}")
    private String fastApiBaseUrl;

    private final WebClient client = WebClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
            .build();

    /** Ping LangGraph every 14min (first ping 30s after boot). */
    @Scheduled(initialDelay = 30_000, fixedRate = 840_000)
    public void ping() {
        String baseUrl = fastApiBaseUrl.replaceAll("/+$", "");
        String pingUrl = baseUrl + "/ping";

        log.info("[Keep-Alive] HEAD {}", pingUrl);

        client.method(HttpMethod.HEAD)
                .uri(pingUrl)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(resp -> log.info("[Keep-Alive] /ping status {}", resp.getStatusCode()))
                .onErrorResume(err -> {
                    log.warn("[Keep-Alive] /ping failed ({}), trying HEAD /", err.getMessage());
                    return client.method(HttpMethod.HEAD)
                            .uri(baseUrl)
                            .retrieve()
                            .toBodilessEntity()
                            .doOnSuccess(resp -> log.info("[Keep-Alive] / status {}", resp.getStatusCode()))
                            .doOnError(e -> log.warn("[Keep-Alive] fallback / failed: {}", e.getMessage()));
                })
                .subscribe();
    }
}
