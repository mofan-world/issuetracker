package com.example.issuetracker.repository;

import com.example.issuetracker.domain.TicketAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    @EntityGraph(attributePaths = {"uploader"})
    List<TicketAttachment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    @Override
    @EntityGraph(attributePaths = {"ticket", "ticket.creator", "ticket.assignee", "uploader"})
    Optional<TicketAttachment> findById(Long id);

    long countByTicketId(Long ticketId);
}

