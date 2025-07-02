package com.user.management.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.util.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String env = System.getenv("ENV"); // ENV=render or local
        List<String> allowedOrigins = new ArrayList<>();

        if ("render".equalsIgnoreCase(env)) {
            allowedOrigins.add("https://react-frontend-xyz.onrender.com");   // React frontend Render URL
        } else {
            allowedOrigins.add("http://localhost:8080");
            allowedOrigins.add("http://localhost:8085");
        }

        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
