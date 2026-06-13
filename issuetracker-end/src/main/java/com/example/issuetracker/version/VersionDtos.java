package com.example.issuetracker.version;

import com.example.issuetracker.domain.ProductVersionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class VersionDtos {

    private VersionDtos() {
    }

    public record SaveVersionRequest(
            @NotBlank
            @Size(max = 50)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "版本号只能包含字母、数字、点、下划线和短横线")
            String versionNo,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 5000) String description,
            @NotNull ProductVersionStatus status,
            LocalDate releaseDate,
            boolean enabled,
            Long parentId
    ) {
    }

    public record VersionView(
            Long id,
            String versionNo,
            String name,
            String description,
            ProductVersionStatus status,
            LocalDate releaseDate,
            boolean enabled,
            Long parentId,
            String parentVersionNo,
            int depth,
            String pathLabel,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record VersionOption(
            Long id,
            String versionNo,
            String name,
            ProductVersionStatus status,
            Long parentId,
            boolean enabled,
            int depth,
            String pathLabel
    ) {
    }
}
