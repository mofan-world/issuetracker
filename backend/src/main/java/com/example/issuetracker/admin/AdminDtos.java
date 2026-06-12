package com.example.issuetracker.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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

    public record UserOption(Long id, String username, String displayName) {
    }
}

