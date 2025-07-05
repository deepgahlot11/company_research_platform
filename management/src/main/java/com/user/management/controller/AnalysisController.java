package com.user.management.controller;

import com.user.management.dto.AnalyzeRequest;
import com.user.management.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

  /* ------------------------------------------------------------------ */
  /*  CONFIG                                                            */
  /* ------------------------------------------------------------------ */

  @Value("${fastapi.base-url}")   private String fastApiBaseUrl; // e.g. https://langgraph-agent-xxx.onrender.com
  @Value("${fastapi.secret-key}") private String secretKey;      // must match FastAPI SECURITY_HEADER
  private final JwtService        jwtService;

  private static final Duration WARM_MAX   = Duration.ofSeconds(60);
  private static final Duration WARM_BACK  = Duration.ofSeconds(8);

  /* single, reusable WebClient */
  private WebClient client() {
    return WebClient.builder()
            .defaultHeader("x-internal-key", secretKey)
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
            .build();
  }

  /* ------------------------------------------------------------------ */
  /*  Simple POST  /analyze                                             */
  /* ------------------------------------------------------------------ */
  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(@RequestBody AnalyzeRequest req,
                                   HttpServletRequest httpReq) {

    RestTemplate rt = new RestTemplate();
    HttpHeaders h  = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.set("x-internal-key", secretKey);

    try {
      var resp = rt.postForEntity(
              fastApiBaseUrl + "/analyze", new HttpEntity<>(req, h), String.class);
      return ResponseEntity.ok(resp.getBody());

    } catch (HttpClientErrorException e) {
      return ResponseEntity.status(e.getStatusCode())
              .body("FastAPI error: " + e.getResponseBodyAsString());
    } catch (Exception ex) {
      return ResponseEntity.internalServerError().body("Internal error: " + ex.getMessage());
    }
  }

  /* ------------------------------------------------------------------ */
  /*  Server‑Sent Events  /stream                                       */
  /* ------------------------------------------------------------------ */
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> stream(@RequestParam String company,
                             @RequestParam(required = false) String extraction_schema,
                             @RequestParam(required = false) String user_notes,
                             @RequestParam(required = false) String token) {

    /* JWT guard */
    if (!jwtService.validateToken(token)) {
      return Flux.error(new IllegalArgumentException("Invalid JWT"));
    }

    /* --- build URIs ------------------------------------------------ */
    String base = fastApiBaseUrl.replaceAll("/+$", "");
    URI streamUri = UriComponentsBuilder.fromHttpUrl(base)
            .path("/analyze/stream")
            .queryParam("company", company)
            .queryParamIfPresent("extraction_schema",
                    Optional.ofNullable(extraction_schema).filter(s -> !s.isBlank()))
            .queryParamIfPresent("user_notes",
                    Optional.ofNullable(user_notes).filter(s -> !s.isBlank()))
            .build(false).toUri();

    URI pingUri = URI.create(base + "/ping");  // very light endpoint
    URI rootUri = URI.create(base);            // fallback

    WebClient wc = client();

    log.info("LangGraph base      : {}", base);
    log.info("Requesting STREAM → : {}", streamUri);

    /* --- 1️⃣  cold‑start warm‑up (HEAD) ---------------------------- */
    Mono<Void> warmUp = wc.method(HttpMethod.HEAD).uri(pingUri)
            .exchangeToMono(resp -> Mono.just(resp.statusCode()))
            .onErrorResume(ex -> {
              log.warn("HEAD /ping failed ({}) → trying HEAD /", ex.getMessage());
              return wc.method(HttpMethod.HEAD).uri(rootUri)
                      .exchangeToMono(r -> Mono.just(r.statusCode()));
            })
            .retryWhen(
                    Retry.fixedDelay(Integer.MAX_VALUE, WARM_BACK)
                            .filter(this::shouldRetry)
                            .maxBackoff(WARM_MAX))
            .timeout(WARM_MAX)
            .doOnSubscribe(s -> log.info("Cold‑start ping …"))
            .doOnSuccess(code -> log.info("Ping ready: HTTP {}", code))
            .then();

    /* --- 2️⃣  actual SSE stream ------------------------------------ */
    return warmUp.thenMany(
            wc.get().uri(streamUri)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .retryWhen(
                            Retry.fixedDelay(3, Duration.ofSeconds(3))
                                    .filter(this::shouldRetry))
                    .doOnSubscribe(s -> log.info("SSE stream started …"))
                    .doOnError(e -> log.error("SSE stream failed: {}", e.getMessage())));
  }

  /* helper: retry on 5xx, connect‑fail, or 502/504 etc. */
  private boolean shouldRetry(Throwable t) {
    if (t instanceof WebClientResponseException we) {
      return we.getStatusCode().is5xxServerError() || we.getStatusCode() == HttpStatus.BAD_GATEWAY;
    }
    return t.getMessage().contains("Connection") || t.getMessage().contains("refused");
  }
}
