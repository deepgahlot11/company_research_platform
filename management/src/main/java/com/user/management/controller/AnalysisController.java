package com.user.management.controller;

import com.user.management.dto.AnalyzeRequest;
import com.user.management.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

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

    System.out.println("JWT token: " + token);
    if (!jwtService.validateToken(token)) {
      throw new IllegalArgumentException("Invalid token in the request...");
    }

    // Build the URI with query parameters
    UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl(FASTAPI_BASE_URL + "/analyze/stream")
            .queryParam("company", company);

    if (extraction_schema != null && !extraction_schema.isEmpty()) {
      builder.queryParam("extraction_schema", extraction_schema);
    }
    if (user_notes != null && !user_notes.isEmpty()) {
      builder.queryParam("user_notes", user_notes);
    }

    URI fastApiUri = builder.build(false).toUri();

    // Build WebClient with headers
    WebClient webClient = WebClient.builder()
            .defaultHeader("x-internal-key", SECRET_KEY)
            .build();

    // Retry logic with exponential backoff
    return webClient.get()
            .uri(fastApiUri)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(String.class)
            .retryWhen(Retry.backoff(5, Duration.ofSeconds(7))
                    .filter(error -> {
                      System.err.println("Retrying due to: " + error.getMessage());
                      return true; // retry all errors, or filter by type if needed
                    }))
            .doOnError(error -> {
              System.err.println("Final failure calling LangGraph agent: " + error.getMessage());
            });
  }

}
