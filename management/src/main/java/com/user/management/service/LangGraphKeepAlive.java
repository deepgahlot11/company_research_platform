package com.user.management.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class LangGraphKeepAlive {

    @Value("${fastapi.base-url}")  private String fastApiBaseUrl;
    private final WebClient keepAliveClient = WebClient.create();

    /** Ping LangGraph every 14min (first ping 30s after boot). */
    @Scheduled(initialDelay = 30_000, fixedRate = 840_000)   // 14*60*1000
    public void ping() {
        String url = fastApiBaseUrl.replaceAll("/+$", "");
        keepAliveClient.get().uri(url + "/ping")
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("[Keep‑Alive] OK {}", r.getStatusCode()))
                .doOnError(e -> log.warn("[Keep‑Alive] {}", e.getMessage()))
                .subscribe();
    }
}
