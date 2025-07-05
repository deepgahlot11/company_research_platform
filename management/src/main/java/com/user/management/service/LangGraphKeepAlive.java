package com.user.management.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class LangGraphKeepAlive {

    @Value("${fastapi.base-url}")
    private String fastApiBaseUrl;

    private final WebClient client = WebClient.create();

    /** Ping LangGraph every 60min (first ping 30s after boot). */
    @Scheduled(initialDelay = 30_000, fixedRate = 840_000)
    public void ping() {
        String url = fastApiBaseUrl.replaceAll("/+$", "");
        log.info("[Keep‑Alive] ping {}", url);

        WebClient client = WebClient.builder().defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").build();

        client.get()
                .uri(url)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(resp -> log.info("[Keep‑Alive] status {}", resp.getStatusCode()))
                .doOnError(err -> log.warn("[Keep‑Alive] failed: {}", err.getMessage()))
                .subscribe();
    }
}
