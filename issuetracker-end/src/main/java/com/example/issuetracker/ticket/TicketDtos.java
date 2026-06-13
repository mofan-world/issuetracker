package com.example.issuetracker.ticket;

import com.example.issuetracker.domain.TicketPriority;
import com.example.issuetracker.domain.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String description,
            @NotBlank @Size(max = 50) String category,
            @NotNull TicketPriority priority,
            @NotNull Long affectedVersionId,
            @NotNull Long projectId
    ) {
    }

    public record UpdateTicketRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String description,
            @NotBlank @Size(max = 50) String category,
            @NotNull TicketPriority priority,
            @NotNull Long affectedVersionId,
            @NotNull Long version
    ) {
    }

    public record AssignRequest(
            @NotNull Long assigneeId,
            @NotNull Long version,
            @Size(max = 1000) String comment
    ) {
    }

    public record ActionRequest(
            @NotNull Long version,
            @Size(max = 1000) String comment
    ) {
    }

    public record ResolveRequest(
            @NotNull Long version,
            @NotBlank @Size(max = 20000) String resolution,
            @NotNull Long resolvedVersionId
    ) {
    }

    public record VerifyRequest(
            @NotNull Long version,
            boolean passed,
            @NotBlank @Size(max = 1000) String comment
    ) {
    }

    public record UserSummary(Long id, String username, String displayName) {
    }

    public record VersionSummary(Long id, String versionNo, String name) {
    }

    public record ProjectSummary(Long id, String code, String name) {
    }

    public record AttachmentView(
            Long id,
            String originalName,
            String contentType,
            long fileSize,
            UserSummary uploader,
            Instant createdAt
    ) {
    }

    public record TicketSummary(
            Long id,
            String ticketNo,
            String title,
            String category,
            TicketPriority priority,
            TicketStatus status,
            ProjectSummary project,
            UserSummary creator,
            UserSummary assignee,
            VersionSummary affectedVersion,
            VersionSummary resolvedVersion,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt
    ) {
    }

    public record TransitionView(
            Long id,
            TicketStatus fromStatus,
            TicketStatus toStatus,
            String action,
            String comment,
            UserSummary operator,
            Instant createdAt
    ) {
    }

    public record TicketDetail(
            Long id,
            String ticketNo,
            String title,
            String description,
            String category,
            TicketPriority priority,
            TicketStatus status,
            ProjectSummary project,
            UserSummary creator,
            UserSummary assignee,
            VersionSummary affectedVersion,
            VersionSummary resolvedVersion,
            String resolution,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            Instant verifiedAt,
            Instant closedAt,
            List<TransitionView> transitions,
            List<AttachmentView> attachments
    ) {
    }

    public record PageResult<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}

