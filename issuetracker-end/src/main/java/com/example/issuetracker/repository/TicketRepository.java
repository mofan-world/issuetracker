package com.example.issuetracker.repository;

import com.example.issuetracker.domain.Ticket;
import com.example.issuetracker.domain.TicketPriority;
import com.example.issuetracker.domain.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    @Override
    @EntityGraph(attributePaths = {"creator", "assignee"})
    Optional<Ticket> findById(Long id);

    @EntityGraph(attributePaths = {"creator", "assignee"})
    Optional<Ticket> findByTicketNo(String ticketNo);

    @Query("""
            select t from Ticket t
            left join fetch t.creator
            left join fetch t.assignee
            where t.id in :ids
            """)
    List<Ticket> findAllWithUsersByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select t from Ticket t
            where (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:userId is null or t.creator.id = :userId or t.assignee.id = :userId)
            """)
    Page<Ticket> search(
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query("""
            select t from Ticket t
            where (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:userId is null or t.creator.id = :userId or t.assignee.id = :userId)
              and (
                lower(t.title) like lower(concat('%', :keyword, '%'))
                or lower(t.description) like lower(concat('%', :keyword, '%'))
                or lower(t.ticketNo) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Ticket> searchWithKeyword(
            @Param("keyword") String keyword,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("userId") Long userId,
            Pageable pageable
    );
}
