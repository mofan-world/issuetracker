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

import java.util.ArrayList;
import java.util.Comparator;
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
                        version.getStatus(),
                        version.getParent() == null ? null : version.getParent().getId(),
                        depth(version),
                        pathLabel(version)
                ))
                .sorted(Comparator.comparing(VersionOption::pathLabel))
                .toList();
    }

    @Transactional
    public VersionView create(SaveVersionRequest request) {
        if (versionRepository.existsByVersionNoIgnoreCase(request.versionNo().trim())) {
            throw BusinessException.badRequest("VERSION_EXISTS", "版本号已存在");
        }
        ProductVersion version = new ProductVersion();
        apply(version, request, null);
        version.setCreatedBy(currentUser.require());
        return toView(versionRepository.save(version));
    }

    @Transactional
    public VersionView update(Long id, SaveVersionRequest request) {
        ProductVersion version = requireVersion(id);
        if (versionRepository.existsByVersionNoIgnoreCaseAndIdNot(request.versionNo().trim(), id)) {
            throw BusinessException.badRequest("VERSION_EXISTS", "版本号已存在");
        }
        apply(version, request, id);
        return toView(versionRepository.save(version));
    }

    @Transactional
    public void delete(Long id) {
        ProductVersion version = requireVersion(id);
        if (ticketRepository.countByAffectedVersionIdOrResolvedVersionId(id, id) > 0) {
            throw BusinessException.badRequest("VERSION_IN_USE", "该版本已被问题单引用，不能删除，可改为停用或归档");
        }
        if (versionRepository.existsByParentId(id)) {
            throw BusinessException.badRequest("VERSION_HAS_CHILDREN", "该版本存在子版本，不能删除");
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

    private void apply(ProductVersion version, SaveVersionRequest request, Long currentId) {
        ProductVersion parent = resolveParent(request.parentId(), currentId);
        version.setVersionNo(request.versionNo().trim());
        version.setName(request.name().trim());
        version.setDescription(request.description() == null ? null : request.description().trim());
        version.setStatus(request.status());
        version.setReleaseDate(request.releaseDate());
        version.setEnabled(request.enabled());
        version.setParent(parent);
        int subtreeHeight = currentId == null ? 1 : subtreeHeight(currentId, 1);
        if (depth(version) + subtreeHeight - 1 > 5) {
            throw BusinessException.badRequest("VERSION_DEPTH_EXCEEDED", "版本层级最多为 5 层");
        }
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
                version.getParent() == null ? null : version.getParent().getId(),
                version.getParent() == null ? null : version.getParent().getVersionNo(),
                depth(version),
                pathLabel(version),
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }

    private ProductVersion resolveParent(Long parentId, Long currentId) {
        if (parentId == null) {
            return null;
        }
        ProductVersion parent = requireVersion(parentId);
        ProductVersion cursor = parent;
        int parentDepth = 0;
        while (cursor != null) {
            parentDepth++;
            if (currentId != null && currentId.equals(cursor.getId())) {
                throw BusinessException.badRequest("VERSION_CYCLE", "父版本不能是当前版本或其子版本");
            }
            if (parentDepth >= 5) {
                throw BusinessException.badRequest("VERSION_DEPTH_EXCEEDED", "版本层级最多为 5 层");
            }
            cursor = cursor.getParent();
        }
        return parent;
    }

    private int depth(ProductVersion version) {
        int depth = 1;
        ProductVersion cursor = version.getParent();
        while (cursor != null) {
            depth++;
            if (depth > 5) {
                throw BusinessException.badRequest("VERSION_DEPTH_EXCEEDED", "版本层级最多为 5 层");
            }
            cursor = cursor.getParent();
        }
        return depth;
    }

    private String pathLabel(ProductVersion version) {
        List<String> parts = new ArrayList<>();
        ProductVersion cursor = version;
        int guard = 0;
        while (cursor != null && guard++ < 5) {
            parts.add(0, cursor.getVersionNo());
            cursor = cursor.getParent();
        }
        return String.join(" / ", parts);
    }

    private int subtreeHeight(Long versionId, int level) {
        if (level > 5) {
            throw BusinessException.badRequest("VERSION_DEPTH_EXCEEDED", "版本层级最多为 5 层");
        }
        List<ProductVersion> children = versionRepository.findByParentId(versionId);
        int height = 1;
        for (ProductVersion child : children) {
            height = Math.max(height, 1 + subtreeHeight(child.getId(), level + 1));
        }
        return height;
    }
}
