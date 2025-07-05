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

  /* --------------------------------------------------------------------- */
  /*  CONFIG                                                               */
  /* --------------------------------------------------------------------- */

  @Value("${fastapi.base-url}")  private String fastApiBaseUrl;  // e.g. https://langgraph‑agent-xxx.onrender.com
  @Value("${fastapi.secret-key}") private String secretKey;      // must match FastAPI SECURITY_HEADER
  private final JwtService jwtService;

  private static final Duration WARM_UP_MAX_WAIT   = Duration.ofSeconds(60);
  private static final Duration WARM_UP_RETRY_BACK = Duration.ofSeconds(8);

  private WebClient webClient() {
    return WebClient.builder()
            .defaultHeader("x-internal-key", secretKey)
            .defaultHeader(HttpHeaders.USER_AGENT, "SpringBoot‑Analysis‑Service")
            .build();
  }

  /* --------------------------------------------------------------------- */
  /*  SIMPLE POST  /analyze                                                */
  /* --------------------------------------------------------------------- */
  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(@RequestBody AnalyzeRequest req,
                                   HttpServletRequest httpReq) {

    RestTemplate rt = new RestTemplate();
    HttpHeaders h = new HttpHeaders();
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

  /* --------------------------------------------------------------------- */
  /*  SSE /stream                                                          */
  /* --------------------------------------------------------------------- */
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> stream(@RequestParam String company,
                             @RequestParam(required = false) String extraction_schema,
                             @RequestParam(required = false) String user_notes,
                             @RequestParam(required = false) String token) {

    /* JWT guard */
    if (!jwtService.validateToken(token)) {
      return Flux.error(new IllegalArgumentException("Invalid JWT"));
    }

    /* URL building */
    String base = fastApiBaseUrl.replaceAll("/+$", "");
    URI streamUri = UriComponentsBuilder.fromHttpUrl(base)
            .path("/analyze/stream")
            .queryParam("company", company)
            .queryParamIfPresent("extraction_schema",
                    Optional.ofNullable(extraction_schema).filter(s -> !s.isBlank()))
            .queryParamIfPresent("user_notes",
                    Optional.ofNullable(user_notes).filter(s -> !s.isBlank()))
            .build(false).toUri();

    URI pingUri = URI.create(base + "/ping");     // lightweight endpoint we added to FastAPI
    URI rootUri = URI.create(base);               // fallback (Render also wakes on “/”)

    WebClient client = webClient();

    log.info("LangGraph base      : {}", base);
    log.info("Requesting STREAM → : {}", streamUri);

    /* -------------------- 1. cold‑start warm‑up ----------------------- */
    Mono<Void> warmUp =
            client.get().uri(pingUri)                 // first try /ping
                    .exchangeToMono(resp -> Mono.just(resp.statusCode()))
                    .onErrorResume(ex -> {
                      log.warn("Ping /ping failed: {} → will try /", ex.getMessage());
                      return client.get().uri(rootUri).exchangeToMono(r -> Mono.just(r.statusCode()));
                    })
                    .doOnSubscribe(s -> log.info("Cold‑start ping …"))
                    .retryWhen(
                            Retry.fixedDelay(Integer.MAX_VALUE, WARM_UP_RETRY_BACK)
                                    .filter(this::is5xxOrConnect)
                                    .maxBackoff(WARM_UP_MAX_WAIT))
                    .timeout(WARM_UP_MAX_WAIT)
                    .doOnSuccess(code -> log.info("Ping ready: HTTP {}", code))
                    .then();

    /* -------------------- 2. actual SSE call -------------------------- */
    return warmUp.thenMany(
            client.get().uri(streamUri)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .retryWhen(
                            Retry.fixedDelay(3, Duration.ofSeconds(3))
                                    .filter(this::is5xxOrConnect))
                    .doOnSubscribe(s -> log.info("SSE stream started …"))
                    .doOnError(e -> log.error("SSE stream failed: {}", e.getMessage())));
  }

  /* Utils */
  private boolean is5xxOrConnect(Throwable t) {
    return (t instanceof WebClientResponseException we && we.getStatusCode().is5xxServerError())
            || t.getMessage().contains("Connection refused");
  }
}
