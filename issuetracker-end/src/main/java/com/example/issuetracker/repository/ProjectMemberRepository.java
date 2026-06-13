package com.example.issuetracker.repository;

import com.example.issuetracker.domain.Project;
import com.example.issuetracker.domain.ProjectMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    long countByProjectId(Long projectId);

    void deleteByProjectIdAndUserId(Long projectId, Long userId);

    @Query("""
            select pm.project from ProjectMember pm
            where pm.user.id = :userId and pm.project.enabled = true
            order by pm.project.name
            """)
    List<Project> findEnabledProjectsByUserId(@Param("userId") Long userId);

    @Query(
            value = """
            select pm from ProjectMember pm
            join fetch pm.user u
            where pm.project.id = :projectId
              and u.deleted = false
              and (
                :keyword = ''
                or lower(u.username) like lower(concat('%', :keyword, '%'))
                or lower(u.displayName) like lower(concat('%', :keyword, '%'))
                or lower(u.email) like lower(concat('%', :keyword, '%'))
              )
            order by u.displayName, u.username
            """,
            countQuery = """
            select count(pm) from ProjectMember pm
            join pm.user u
            where pm.project.id = :projectId
              and u.deleted = false
              and (
                :keyword = ''
                or lower(u.username) like lower(concat('%', :keyword, '%'))
                or lower(u.displayName) like lower(concat('%', :keyword, '%'))
                or lower(u.email) like lower(concat('%', :keyword, '%'))
              )
            """
    )
    Page<ProjectMember> searchMembers(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select pm from ProjectMember pm
            join fetch pm.user
            where pm.project.id = :projectId and pm.user.id in :userIds
            """)
    List<ProjectMember> findByProjectIdAndUserIdIn(
            @Param("projectId") Long projectId,
            @Param("userIds") Collection<Long> userIds
    );

    @Modifying
    @Query(value = """
            insert into project_members(project_id, user_id, created_at, updated_at)
            select :targetProjectId, pm.user_id, current_timestamp, current_timestamp
            from project_members pm
            join users u on u.id = pm.user_id
            where pm.project_id = :sourceProjectId
              and u.deleted = false
              and u.enabled = true
            on conflict (project_id, user_id) do nothing
            """, nativeQuery = true)
    int copyMembers(
            @Param("sourceProjectId") Long sourceProjectId,
            @Param("targetProjectId") Long targetProjectId
    );
}
