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
    @EntityGraph(attributePaths = {"project", "creator", "assignee", "affectedVersion", "resolvedVersion"})
    Optional<Ticket> findById(Long id);

    @EntityGraph(attributePaths = {"project", "creator", "assignee", "affectedVersion", "resolvedVersion"})
    Optional<Ticket> findByTicketNo(String ticketNo);

    @Query("""
            select t from Ticket t
            left join fetch t.creator
            left join fetch t.assignee
            join fetch t.project
            left join fetch t.affectedVersion
            left join fetch t.resolvedVersion
            where t.id in :ids and t.project.id = :projectId
            """)
    List<Ticket> findAllWithUsersByIdIn(
            @Param("ids") Collection<Long> ids,
            @Param("projectId") Long projectId
    );

    @Query("""
            select t from Ticket t
            where t.project.id = :projectId
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:visibilityUserId is null or t.creator.id = :visibilityUserId or t.assignee.id = :visibilityUserId)
              and (:creatorId is null or t.creator.id = :creatorId)
            """)
    Page<Ticket> search(
            @Param("projectId") Long projectId,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("visibilityUserId") Long visibilityUserId,
            @Param("creatorId") Long creatorId,
            Pageable pageable
    );

    @Query("""
            select t from Ticket t
            where t.project.id = :projectId
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:visibilityUserId is null or t.creator.id = :visibilityUserId or t.assignee.id = :visibilityUserId)
              and (:creatorId is null or t.creator.id = :creatorId)
              and (
                lower(t.title) like lower(concat('%', :keyword, '%'))
                or lower(t.description) like lower(concat('%', :keyword, '%'))
                or lower(t.ticketNo) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Ticket> searchWithKeyword(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("visibilityUserId") Long visibilityUserId,
            @Param("creatorId") Long creatorId,
            Pageable pageable
    );

    long countByAffectedVersionIdOrResolvedVersionId(Long affectedVersionId, Long resolvedVersionId);
}
