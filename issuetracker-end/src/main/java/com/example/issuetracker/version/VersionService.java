package com.example.issuetracker.version;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.domain.ProductVersion;
import com.example.issuetracker.repository.ProductVersionRepository;
import com.example.issuetracker.repository.TicketRepository;
import com.example.issuetracker.security.CurrentUser;
import com.example.issuetracker.ticket.TicketDtos.PageResult;
import com.example.issuetracker.version.VersionDtos.SaveVersionRequest;
import com.example.issuetracker.version.VersionDtos.VersionOption;
import com.example.issuetracker.version.VersionDtos.VersionView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionService {

    private final ProductVersionRepository versionRepository;
    private final TicketRepository ticketRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResult<VersionView> list(String keyword, int page, int size) {
        String query = keyword == null ? "" : keyword.trim();
        var pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        var result = versionRepository.findByVersionNoContainingIgnoreCaseOrNameContainingIgnoreCase(
                query, query, pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toView).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<VersionOption> options() {
        return versionRepository.findByEnabledTrueOrderByCreatedAtDesc().stream()
                .map(version -> new VersionOption(
                        version.getId(),
                        version.getVersionNo(),
                        version.getName(),
                        version.getStatus()
                ))
                .toList();
    }

    @Transactional
    public VersionView create(SaveVersionRequest request) {
        if (versionRepository.existsByVersionNoIgnoreCase(request.versionNo().trim())) {
            throw BusinessException.badRequest("VERSION_EXISTS", "版本号已存在");
        }
        ProductVersion version = new ProductVersion();
        apply(version, request);
        version.setCreatedBy(currentUser.require());
        return toView(versionRepository.save(version));
    }

    @Transactional
    public VersionView update(Long id, SaveVersionRequest request) {
        ProductVersion version = requireVersion(id);
        if (versionRepository.existsByVersionNoIgnoreCaseAndIdNot(request.versionNo().trim(), id)) {
            throw BusinessException.badRequest("VERSION_EXISTS", "版本号已存在");
        }
        apply(version, request);
        return toView(versionRepository.save(version));
    }

    @Transactional
    public void delete(Long id) {
        ProductVersion version = requireVersion(id);
        if (ticketRepository.countByAffectedVersionIdOrResolvedVersionId(id, id) > 0) {
            throw BusinessException.badRequest("VERSION_IN_USE", "该版本已被问题单引用，不能删除，可改为停用或归档");
        }
        versionRepository.delete(version);
    }

    public ProductVersion requireEnabled(Long id) {
        return versionRepository.findById(id)
                .filter(ProductVersion::isEnabled)
                .orElseThrow(() -> BusinessException.badRequest("INVALID_VERSION", "所选版本不存在或已停用"));
    }

    private ProductVersion requireVersion(Long id) {
        return versionRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("版本不存在"));
    }

    private void apply(ProductVersion version, SaveVersionRequest request) {
        version.setVersionNo(request.versionNo().trim());
        version.setName(request.name().trim());
        version.setDescription(request.description() == null ? null : request.description().trim());
        version.setStatus(request.status());
        version.setReleaseDate(request.releaseDate());
        version.setEnabled(request.enabled());
    }

    private VersionView toView(ProductVersion version) {
        return new VersionView(
                version.getId(),
                version.getVersionNo(),
                version.getName(),
                version.getDescription(),
                version.getStatus(),
                version.getReleaseDate(),
                version.isEnabled(),
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }
}

