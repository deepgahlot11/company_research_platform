package com.user.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank String firstName, @NotBlank  String lastName, @Email @NotBlank String email,@NotBlank @Size(min = 6) String password) {}
