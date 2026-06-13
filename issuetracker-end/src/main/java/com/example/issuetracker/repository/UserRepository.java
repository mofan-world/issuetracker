package com.example.issuetracker.repository;

import com.example.issuetracker.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where u.id = :id and u.deleted = false")
    Optional<User> findWithRolesById(@Param("id") Long id);

    @Query("""
            select u from User u
            where u.deleted = false
              and (
                lower(u.username) like lower(concat('%', :keyword, '%'))
                or lower(u.displayName) like lower(concat('%', :keyword, '%'))
                or lower(u.email) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<User> searchActiveUsers(@Param("keyword") String keyword, Pageable pageable);
}

