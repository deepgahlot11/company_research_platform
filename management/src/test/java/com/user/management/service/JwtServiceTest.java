package com.user.management.service;

import com.user.management.entity.User;
import com.user.management.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit‑tests for {@link JwtService}.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String ISSUER = "user-management-api";
    private static final String USER_EMAIL = "test@example.com";

    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;   // will be injected into service

    private User mockUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(userRepository);
        // Inject the issuer value that would normally come from application.properties
        ReflectionTestUtils.setField(jwtService, "issuer", ISSUER);

        mockUser = new User();
        mockUser.setEmail(USER_EMAIL);
        mockUser.setPassword("password123");
        mockUser.setFirstName("Test");
        mockUser.setLastName("User");
    }

    /* ------------------------------------------------------------------ */
    /*  generate / extract                                                */
    /* ------------------------------------------------------------------ */

    @Test
    void generateToken_containsSubjectAndIssuer() {
        String token = jwtService.generateToken(mockUser);
        assertNotNull(token);

        Claims claims = jwtService.getClaims(token);
        assertEquals(USER_EMAIL, claims.getSubject());
        assertEquals(ISSUER, claims.getIssuer());
    }

    @Test
    void extractUsername_returnsCorrectEmail() {
        String token = jwtService.generateToken(mockUser);
        assertEquals(USER_EMAIL, jwtService.extractUsername(token));
    }

    /* ------------------------------------------------------------------ */
    /*  isTokenValid                                                      */
    /* ------------------------------------------------------------------ */

    @Test
    void isTokenValid_trueForMatchingUser() {
        String token = jwtService.generateToken(mockUser);
        assertTrue(jwtService.isTokenValid(token, mockUser));
    }

    @Test
    void isTokenValid_falseForMismatchedUser() {
        String token = jwtService.generateToken(mockUser);

        User another = new User();
        another.setEmail("other@example.com");

        assertFalse(jwtService.isTokenValid(token, another));
    }

    /* ------------------------------------------------------------------ */
    /*  validateToken                                                     */
    /* ------------------------------------------------------------------ */

    @Test
    void validateToken_trueWhenUserExistsAndIssuerMatches() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));

        String token = jwtService.generateToken(mockUser);
        assertTrue(jwtService.validateToken(token));

        verify(userRepository).findByEmail(USER_EMAIL);
    }

    @Test
    void validateToken_falseWhenUserNotInDb() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

        String token = jwtService.generateToken(mockUser);
        assertFalse(jwtService.validateToken(token));
    }

    @Test
    void validateToken_falseWhenIssuerIsWrong() {
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));

        // manually craft a token with a wrong issuer
        String badIssuerToken = Jwts.builder()
                .setSubject(USER_EMAIL)
                .setIssuer("some-other-issuer")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(jwtService.getSignKey(), SignatureAlgorithm.HS512)
                .compact();

        assertFalse(jwtService.validateToken(badIssuerToken));
    }
}
