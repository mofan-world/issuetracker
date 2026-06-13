package com.example.issuetracker.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record RoleView(Long id, String code, String name, List<String> permissions) {
    }

    public record UserView(
            Long id,
            String username,
            String email,
            String displayName,
            boolean enabled,
            List<String> roles,
            Instant createdAt
    ) {
    }

    public record UpdateRolesRequest(@NotEmpty Set<@NotNull Long> roleIds) {
    }

    public record UpdateEnabledRequest(boolean enabled) {
    }

    public record CreateUserRequest(
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9_]{4,50}$", message = "用户名只能包含字母、数字和下划线，长度为4-50")
            String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank
            @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
            String password,
            boolean enabled,
            @NotEmpty Set<@NotNull Long> roleIds
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9_]{4,50}$", message = "用户名只能包含字母、数字和下划线，长度为4-50")
            String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String displayName,
            @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
            String password,
            boolean enabled,
            @NotEmpty Set<@NotNull Long> roleIds
    ) {
    }

    public record UserOption(Long id, String username, String displayName) {
    }
}

