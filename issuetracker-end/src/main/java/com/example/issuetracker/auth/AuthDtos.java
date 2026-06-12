package com.example.issuetracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9_]{4,50}$", message = "用户名只能包含字母、数字和下划线，长度为4-50")
            String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank
            @Size(min = 8, max = 72)
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "密码必须同时包含字母和数字"
            )
            String password,
            @NotBlank @Size(max = 100) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt,
            UserProfile user
    ) {
    }

    public record UserProfile(
            Long id,
            String username,
            String email,
            String displayName,
            List<String> roles,
            List<String> permissions
    ) {
    }
}

