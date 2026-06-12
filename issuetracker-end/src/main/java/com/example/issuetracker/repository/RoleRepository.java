package com.example.issuetracker.repository;

import com.example.issuetracker.domain.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByCode(String code);

    @Override
    @EntityGraph(attributePaths = "permissions")
    List<Role> findAll();
}

