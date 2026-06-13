package com.example.issuetracker.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectView(
            Long id,
            String code,
            String name,
            String description,
            boolean enabled,
            long memberCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ProjectMemberView(
            Long id,
            String username,
            String displayName,
            String email,
            List<String> roles,
            Instant joinedAt
    ) {
    }

    public record ProjectUserOption(Long id, String username, String displayName) {
    }

    public record CreateProjectRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$", message = "项目编码只能包含字母、数字、下划线和短横线")
            String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 5000) String description,
            boolean enabled
    ) {
    }

    public record UpdateProjectRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$", message = "项目编码只能包含字母、数字、下划线和短横线")
            String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 5000) String description,
            boolean enabled
    ) {
    }

    public record AddMembersRequest(@NotEmpty Set<@NotNull Long> userIds) {
    }

    public record CopyMembersRequest(@NotNull Long sourceProjectId) {
    }

    public record ImportMembersResult(
            int addedCount,
            int ignoredCount,
            List<String> notFound
    ) {
    }
}
