package com.user.management.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(@NotBlank String email,@NotBlank String password) {}
