package com.example.issuetracker.ticket;

public record TicketChangedEvent(Long ticketId, boolean deleted) {
}

