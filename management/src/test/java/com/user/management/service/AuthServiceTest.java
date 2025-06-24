package com.user.management.service;

import com.user.management.dto.AuthRequest;
import com.user.management.dto.AuthResponse;
import com.user.management.dto.RegisterRequest;
import com.user.management.entity.User;
import com.user.management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegister_ShouldReturnToken() {
        // Given
        RegisterRequest request = new RegisterRequest("John", "Doe", "john@example.com", "pass123");

        User savedUser = new User();
        savedUser.setFirstName("John");
        savedUser.setLastName("Doe");
        savedUser.setEmail("john@example.com");

        when(passwordEncoder.encode(request.password())).thenReturn("encoded-pass");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("mocked-jwt");

        // When
        AuthResponse response = authService.register(request);

        // Then
        assertNotNull(response);
        assertEquals("mocked-jwt", response.token());

        verify(userRepository).save(any(User.class));
        verify(jwtService).generateToken(any(User.class));
    }

    @Test
    void testAuthenticate_ShouldReturnToken() {
        // Given
        AuthRequest request = new AuthRequest("john@example.com", "pass123");

        User existingUser = new User();
        existingUser.setEmail("john@example.com");
        existingUser.setPassword("encoded-pass");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(existingUser)).thenReturn("mocked-jwt");

        // When
        AuthResponse response = authService.authenticate(request);

        // Then
        assertNotNull(response);
        assertEquals("mocked-jwt", response.token());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail("john@example.com");
    }

    @Test
    void testAuthenticate_InvalidUser_ShouldThrowException() {
        // Given
        AuthRequest request = new AuthRequest("invalid@example.com", "pass123");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        // Then
        assertThrows(NoSuchElementException.class, () -> authService.authenticate(request));

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}
