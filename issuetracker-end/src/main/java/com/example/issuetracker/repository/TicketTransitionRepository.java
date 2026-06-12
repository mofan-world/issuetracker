package com.example.issuetracker.repository;

import com.example.issuetracker.domain.TicketTransition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketTransitionRepository extends JpaRepository<TicketTransition, Long> {

    @EntityGraph(attributePaths = "operator")
    List<TicketTransition> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}

