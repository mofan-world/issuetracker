package com.example.issuetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ticket_transitions")
public class TicketTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus toStatus;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}

