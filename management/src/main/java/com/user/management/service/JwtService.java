package com.user.management.service;

import com.user.management.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtService {

    @Value("${jwt.issuer}")
    private String issuer;

    private final UserRepository userRepository;

    public JwtService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private final String jwtSecret = "1ee61644968f2e5719d7d6e4363e0ff6a0611e4cf3be4f3309e1bea2469abeed57064d408b9d33a58263d0550f193292f10b9a070dadaa437b8c96d4190295b6"; // My name is Django, i am using this app to create user management for learning purposes, I hope this string lenth is enough but even if not enough, who cares, it is just for learning. I am not sure if this length is enough or not. -- https://sha512.online/

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
                .signWith(getSignKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    Key getSignKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);

            // 1. Check expiration
            if (claims.getExpiration().before(new Date())) {
                return false;
            }

            // 2. Check subject (email/user) exists in DB
            String email = claims.getSubject(); // "sub" claim
            if (email == null || userRepository.findByEmail(email).isEmpty()) {
                return false;
            }

            // 3. Check issuer
            String actualIssuer = claims.getIssuer();
            if (!issuer.equals(actualIssuer)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }


}


