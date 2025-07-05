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

  @Value("${fastapi.base-url}")
  private String fastApiBaseUrl; // e.g. https://langgraph‑agent‑xxxx.onrender.com

  @Value("${fastapi.secret-key}")
  private String secretKey; // must match FastAPI’s SECURITY_HEADER

  private final JwtService jwtService;

  // --------------------------------------------------------------------- //
  //  high‑level   /analyze POST  (non‑stream)                             //
  // --------------------------------------------------------------------- //
  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(
      @RequestBody AnalyzeRequest request, HttpServletRequest servletReq) {

    RestTemplate rt = new RestTemplate();
    HttpHeaders hdr = new HttpHeaders();
    hdr.setContentType(MediaType.APPLICATION_JSON);
    hdr.set("x-internal-key", secretKey);
    hdr.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

    try {
      ResponseEntity<String> resp =
          rt.postForEntity(
              fastApiBaseUrl + "/analyze", new HttpEntity<>(request, hdr), String.class);

      return ResponseEntity.ok(resp.getBody());
    } catch (HttpClientErrorException e) {
      return ResponseEntity.status(e.getStatusCode())
              .body("FastAPI error: " + e.getResponseBodyAsString());
    } catch (Exception ex) {
      return ResponseEntity.internalServerError().body("Internal error: " + ex.getMessage());
    }
  }

  // --------------------------------------------------------------------- //
  //  Server‑Sent Events  /stream                                          //
  // --------------------------------------------------------------------- //
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> stream(
      @RequestParam String company,
      @RequestParam(required = false) String extraction_schema,
      @RequestParam(required = false) String user_notes,
      @RequestParam(required = false) String token) {

    // JWT guard
    if (!jwtService.validateToken(token)) {
      return Flux.error(new IllegalArgumentException("Invalid token"));
    }

    long ts0 = System.currentTimeMillis();

    // normalise & build URIs
    String base = fastApiBaseUrl.replaceAll("/+$", "");
    URI streamUri =
        UriComponentsBuilder.fromHttpUrl(base)
            .path("/analyze/stream")
            .queryParam("company", company)
            .queryParamIfPresent(
                "extraction_schema",
                Optional.ofNullable(extraction_schema).filter(s -> !s.isBlank()))
            .queryParamIfPresent(
                "user_notes", Optional.ofNullable(user_notes).filter(s -> !s.isBlank()))
            .build(false)
            .toUri();

    URI pingUri = URI.create(base);

    WebClient client = WebClient.builder().defaultHeader("x-internal-key", secretKey).build();

    log.info("LangGraph agent baseURL      : {}", base);
    log.info("Requesting /analyze/stream → : {}", streamUri);

    // warm‑up ping with exponential back‑off up to ~60s
    Mono<Void> warmUp =
        client
            .post()
            .uri(base + "/analyze")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header("x-internal-key", secretKey)
            .bodyValue(
                """
            {
              "company": "Apple",
              "extraction_schema": {"founded_year": "int", "headquarters": "str", "industry": "str"},
              "user_notes": ""
            }
            """)
            .exchangeToMono(
                resp -> {
                  log.info("Warm-up status: {}", resp.statusCode());
                  return resp.releaseBody();
                })
            .retryWhen(Retry.backoff(6, Duration.ofSeconds(8))) // 6*8 = 48 seconds wait
            .doOnSubscribe(s -> log.info("Pinging LangGraph /analyze to warm-up…"))
            .doOnError(err -> log.warn("Warm-up failed: {}", err.getMessage()))
            .then(Mono.delay(Duration.ofSeconds(3))) // extra wait
            .doOnSuccess(
                v -> log.info("Warm-up complete after {} ms", System.currentTimeMillis() - ts0))
            .then();

    // after warm‑up, start streaming
    return warmUp.thenMany(
        client
            .get()
            .uri(streamUri)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(String.class)
            .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(3)).filter(this::is5xxOr502))
            .doOnSubscribe(s -> log.info("SSE stream started…"))
            .doOnError(err -> log.error("SSE stream failed: {}", err.getMessage())));
  }

  // helper: true for any 5xx (esp. 502) error
  private boolean is5xxOr502(Throwable t) {
    return t instanceof WebClientResponseException we && we.getStatusCode().is5xxServerError();
  }
}
