package com.user.management.controller;

import com.user.management.dto.AnalyzeRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class AnalysisController {

  private final String FASTAPI_URL = "http://host.docker.internal:8000/analyze";
  private final String SECRET_KEY = "spring-secret-key"; // match FastAPI

  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(
      @RequestBody AnalyzeRequest request, HttpServletRequest httpRequest) {
    RestTemplate restTemplate = new RestTemplate();

    // Prepare headers
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-internal-key", SECRET_KEY);

    // Wrap body and headers
    HttpEntity<AnalyzeRequest> httpEntity = new HttpEntity<>(request, headers);

    try {
      ResponseEntity<String> response =
          restTemplate.postForEntity(FASTAPI_URL, httpEntity, String.class);
      return ResponseEntity.ok(response.getBody());
    } catch (HttpClientErrorException e) {
      return ResponseEntity.status(e.getStatusCode())
          .body("FastAPI error: " + e.getResponseBodyAsString());
    } catch (Exception ex) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Internal error: " + ex.getMessage());
    }
  }
}
