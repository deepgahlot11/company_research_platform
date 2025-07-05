package com.user.management.controller;

import com.user.management.dto.AnalyzeRequest;
import com.user.management.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
public class AnalysisController {

  @Value("${fastapi.base-url}")
  private String FASTAPI_BASE_URL;

  @Value("${fastapi.secret-key}")
  private String SECRET_KEY;

  private final JwtService jwtService;

  public AnalysisController(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(
          @RequestBody AnalyzeRequest request, HttpServletRequest httpRequest) {
    RestTemplate restTemplate = new RestTemplate();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-internal-key", SECRET_KEY);

    HttpEntity<AnalyzeRequest> httpEntity = new HttpEntity<>(request, headers);

    try {
      ResponseEntity<String> response =
              restTemplate.postForEntity(FASTAPI_BASE_URL + "/analyze", httpEntity, String.class);
      return ResponseEntity.ok(response.getBody());
    } catch (HttpClientErrorException e) {
      return ResponseEntity.status(e.getStatusCode())
              .body("FastAPI error: " + e.getResponseBodyAsString());
    } catch (Exception ex) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body("Internal error: " + ex.getMessage());
    }
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> stream(
          @RequestParam String company,
          @RequestParam(required = false) String extraction_schema,
          @RequestParam(required = false) String user_notes,
          @RequestParam(required = false) String token) {

    if (!jwtService.validateToken(token)) {
      return Flux.error(new IllegalArgumentException("Invalid token"));
    }

    long requestStart = System.currentTimeMillis();
    log.info("Received /stream request for company='{}'", company);

    // Normalize URL base
    String baseUrl = FASTAPI_BASE_URL.replaceAll("/+$", "");
    log.info("Langgraph agent baseURL - {}", baseUrl);
    URI streamUri = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/analyze/stream")
            .queryParam("company", company)
            .queryParamIfPresent("extraction_schema",
                    Optional.ofNullable(extraction_schema).filter(s -> !s.isEmpty()))
            .queryParamIfPresent("user_notes",
                    Optional.ofNullable(user_notes).filter(s -> !s.isEmpty()))
            .build(false)
            .toUri();

    URI pingUri = URI.create(baseUrl);

    WebClient client = WebClient.builder()
            .defaultHeader("x-internal-key", SECRET_KEY)
            .build();

    // Warm-up call
    Mono<Void> warmUp = client.get()
            .uri(pingUri)
            .exchangeToMono(r -> {
              log.info("Ping status: {}", r.statusCode());
              return r.releaseBody();
            })
            .doOnSubscribe(sub -> log.info("Pinging LangGraph service to warm up…"))
            .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(10)))
            .then(Mono.delay(Duration.ofSeconds(5)))  // Give Render extra buffer time
            .doOnSuccess(v -> log.info("Warm-up complete after {} ms", System.currentTimeMillis() - requestStart))
            .then();

    // Main SSE stream call
    return warmUp.thenMany(
            client.get()
                    .uri(streamUri)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(3))
                            .filter(ex -> ex instanceof WebClientResponseException))
                    .doOnSubscribe(sub -> log.info("Calling /analyze/stream"))
                    .doOnError(err -> log.error("Stream call failed: {}", err.getMessage()))
    );
  }


  @Component
  public class LangGraphKeepAlive {

    @Value("${fastapi.base-url}") // or use FASTAPI_BASE_URL directly
    private String fastapiBaseUrl;

    private final WebClient client = WebClient.create();

    @Scheduled(fixedRate = 60 * 60 * 1000, initialDelay = 15 * 1000) // every 60 min
    public void keepAlive() {
      String url = fastapiBaseUrl.replaceAll("/+$", "");
      log.info("Sending keep-alive ping to LangGraph: {}", url);
      client.get()
              .uri(url)
              .retrieve()
              .toBodilessEntity()
              .doOnSuccess(resp -> log.info("Keep-alive successful, status={}", resp.getStatusCode()))
              .doOnError(err -> log.warn("Keep-alive failed: {}", err.getMessage()))
              .subscribe();
    }
  }

}
