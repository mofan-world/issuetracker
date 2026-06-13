package com.example.issuetracker.repository;

import com.example.issuetracker.domain.ProductVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, Long> {

    boolean existsByVersionNoIgnoreCase(String versionNo);

    boolean existsByVersionNoIgnoreCaseAndIdNot(String versionNo, Long id);

    Page<ProductVersion> findByVersionNoContainingIgnoreCaseOrNameContainingIgnoreCase(
            String versionNo, String name, Pageable pageable);

    List<ProductVersion> findByEnabledTrueOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"parent"})
    List<ProductVersion> findAllByOrderByVersionNoAsc();

    boolean existsByParentId(Long parentId);

    List<ProductVersion> findByParentId(Long parentId);
}
