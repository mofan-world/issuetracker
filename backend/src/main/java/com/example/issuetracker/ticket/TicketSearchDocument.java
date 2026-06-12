package com.example.issuetracker.ticket;

import com.example.issuetracker.domain.TicketPriority;
import com.example.issuetracker.domain.TicketStatus;

import java.time.Instant;

public record TicketSearchDocument(
        Long id,
        String ticketNo,
        String title,
        String description,
        String category,
        TicketPriority priority,
        TicketStatus status,
        Long creatorId,
        Long assigneeId,
        Instant createdAt,
        Instant updatedAt
) {
}

