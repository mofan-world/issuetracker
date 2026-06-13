package com.example.issuetracker.repository;

import com.example.issuetracker.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<Project> findAllByOrderByNameAsc();

    List<Project> findByEnabledTrueOrderByNameAsc();
}
