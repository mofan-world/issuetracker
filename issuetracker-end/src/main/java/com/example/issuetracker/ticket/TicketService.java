package com.example.issuetracker.ticket;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.attachment.TicketAttachmentService;
import com.example.issuetracker.domain.Permission;
import com.example.issuetracker.domain.ProductVersion;
import com.example.issuetracker.domain.Ticket;
import com.example.issuetracker.domain.TicketPriority;
import com.example.issuetracker.domain.TicketStatus;
import com.example.issuetracker.domain.TicketScope;
import com.example.issuetracker.domain.TicketTransition;
import com.example.issuetracker.domain.User;
import com.example.issuetracker.repository.TicketRepository;
import com.example.issuetracker.repository.TicketTransitionRepository;
import com.example.issuetracker.repository.UserRepository;
import com.example.issuetracker.security.CurrentUser;
import com.example.issuetracker.ticket.TicketDtos.ActionRequest;
import com.example.issuetracker.ticket.TicketDtos.AssignRequest;
import com.example.issuetracker.ticket.TicketDtos.CreateTicketRequest;
import com.example.issuetracker.ticket.TicketDtos.PageResult;
import com.example.issuetracker.ticket.TicketDtos.ResolveRequest;
import com.example.issuetracker.ticket.TicketDtos.TicketDetail;
import com.example.issuetracker.ticket.TicketDtos.TicketSummary;
import com.example.issuetracker.ticket.TicketDtos.TransitionView;
import com.example.issuetracker.ticket.TicketDtos.UpdateTicketRequest;
import com.example.issuetracker.ticket.TicketDtos.UserSummary;
import com.example.issuetracker.ticket.TicketDtos.VersionSummary;
import com.example.issuetracker.ticket.TicketDtos.VerifyRequest;
import com.example.issuetracker.version.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final DateTimeFormatter NUMBER_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final TicketRepository ticketRepository;
    private final TicketTransitionRepository transitionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final TicketSearchService searchService;
    private final ApplicationEventPublisher eventPublisher;
    private final VersionService versionService;
    private final TicketAttachmentService attachmentService;

    @Transactional
    public TicketDetail create(CreateTicketRequest request, List<MultipartFile> files) {
        User creator = currentUser.require();
        ProductVersion affectedVersion = versionService.requireEnabled(request.affectedVersionId());
        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setCategory(request.category().trim());
        ticket.setPriority(request.priority());
        ticket.setStatus(TicketStatus.NEW);
        ticket.setCreator(creator);
        ticket.setAffectedVersion(affectedVersion);
        ticketRepository.save(ticket);
        attachmentService.store(ticket, creator, files);
        recordTransition(ticket, creator, null, TicketStatus.NEW, "CREATE", null);
        eventPublisher.publishEvent(new TicketChangedEvent(ticket.getId(), false));
        return toDetail(ticket, transitionRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    @Transactional
    public TicketDetail update(Long id, UpdateTicketRequest request, List<MultipartFile> files) {
        User operator = currentUser.require();
        Ticket ticket = requireTicket(id);
        checkVersion(ticket, request.version());
        boolean metadataModifiable = requireModifiable(ticket, operator);
        if (metadataModifiable) {
            ProductVersion affectedVersion = versionService.requireEnabled(request.affectedVersionId());
            ticket.setTitle(request.title().trim());
            ticket.setCategory(request.category().trim());
            ticket.setPriority(request.priority());
            ticket.setAffectedVersion(affectedVersion);
        }
        ticket.setDescription(request.description().trim());
        ticketRepository.saveAndFlush(ticket);
        attachmentService.store(ticket, operator, files);
        recordTransition(
                ticket,
                operator,
                ticket.getStatus(),
                ticket.getStatus(),
                "UPDATE",
                metadataModifiable ? "更新问题单信息" : "补充问题单描述"
        );
        eventPublisher.publishEvent(new TicketChangedEvent(ticket.getId(), false));
        return toDetail(ticket, transitionRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    @Transactional(readOnly = true)
    public PageResult<TicketSummary> list(
            String keyword,
            TicketStatus status,
            TicketPriority priority,
            TicketScope scope,
            Long requestedCreatorId,
            int page,
            int size
    ) {
        User user = currentUser.require();
        Set<String> permissions = currentUser.permissions(user);
        TicketFilter filter = resolveFilter(user, permissions, scope, requestedCreatorId);
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        if (StringUtils.hasText(keyword)
                && filter.visibilityUserId() == null
                && filter.creatorId() == null
                && status == null
                && priority == null) {
            TicketSearchService.SearchResult searchResult =
                    searchService.search(keyword.trim(), pageable.getPageNumber(), pageable.getPageSize());
            if (searchResult.available()) {
                List<Long> ids = searchResult.ids();
                List<Ticket> tickets = ticketRepository.findAllWithUsersByIdIn(ids);
                tickets.sort(Comparator.comparingInt(ticket -> ids.indexOf(ticket.getId())));
                List<TicketSummary> content = tickets.stream().map(this::toSummary).toList();
                int totalPages = (int) Math.ceil((double) searchResult.total() / pageable.getPageSize());
                return new PageResult<>(
                        content,
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        searchResult.total(),
                        totalPages
                );
            }
        }

        Page<Ticket> result = StringUtils.hasText(keyword)
                ? ticketRepository.searchWithKeyword(
                        keyword.trim(), status, priority,
                        filter.visibilityUserId(), filter.creatorId(), pageable)
                : ticketRepository.search(
                        status, priority, filter.visibilityUserId(), filter.creatorId(), pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public TicketDetail get(Long id) {
        User user = currentUser.require();
        Ticket ticket = requireTicket(id);
        requireVisible(ticket, user);
        return toDetail(ticket, transitionRepository.findByTicketIdOrderByCreatedAtAsc(id));
    }

    @Transactional
    public TicketDetail assign(Long id, AssignRequest request) {
        User operator = currentUser.require();
        Ticket ticket = requireTicket(id);
        checkVersion(ticket, request.version());
        TicketWorkflow.require(ticket.getStatus(), TicketStatus.NEW, TicketStatus.ASSIGNED);
        User assignee = userRepository.findWithRolesById(request.assigneeId())
                .filter(User::isEnabled)
                .orElseThrow(() -> BusinessException.notFound("处理人不存在或已禁用"));
        boolean canProcess = assignee.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .anyMatch("ticket:process"::equals);
        if (!canProcess) {
            throw BusinessException.badRequest("INVALID_ASSIGNEE", "所选用户没有处理问题单的权限");
        }
        TicketStatus from = ticket.getStatus();
        ticket.setAssignee(assignee);
        ticket.setStatus(TicketStatus.ASSIGNED);
        return saveTransition(ticket, operator, from, "ASSIGN", request.comment());
    }

    @Transactional
    public TicketDetail start(Long id, ActionRequest request) {
        User operator = currentUser.require();
        Ticket ticket = requireTicket(id);
        checkVersion(ticket, request.version());
        TicketWorkflow.require(ticket.getStatus(), TicketStatus.ASSIGNED);
        requireAssignee(ticket, operator);
        TicketStatus from = ticket.getStatus();
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        return saveTransition(ticket, operator, from, "START", request.comment());
    }

    @Transactional
    public TicketDetail resolve(Long id, ResolveRequest request) {
        User operator = currentUser.require();
        Ticket ticket = requireTicket(id);
        checkVersion(ticket, request.version());
        TicketWorkflow.require(ticket.getStatus(), TicketStatus.IN_PROGRESS);
        requireAssignee(ticket, operator);
        ProductVersion resolvedVersion = versionService.requireEnabled(request.resolvedVersionId());
        TicketStatus from = ticket.getStatus();
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolution(request.resolution().trim());
        ticket.setResolvedVersion(resolvedVersion);
        ticket.setResolvedAt(Instant.now());
        return saveTransition(
                ticket,
                operator,
                from,
                "RESOLVE",
                "提交解决方案，解决版本: " + resolvedVersion.getVersionNo()
        );
    }

    @Transactional
    public TicketDetail verify(Long id, VerifyRequest request) {
        User operator = currentUser.require();
        Ticket ticket = requireTicket(id);
        checkVersion(ticket, request.version());
        TicketWorkflow.require(ticket.getStatus(), TicketStatus.RESOLVED);
        TicketStatus from = ticket.getStatus();
        if (request.passed()) {
            ticket.setStatus(TicketStatus.VERIFIED);
            ticket.setVerifiedAt(Instant.now());
            return saveTransition(ticket, operator, from, "VERIFY_PASS", request.comment());
        }
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setResolvedVersion(null);
        ticket.setResolvedAt(null);
        ticket.setVerifiedAt(null);
        return saveTransition(ticket, operator, from, "VERIFY_REJECT", request.comment());
    }

    @Transactional
    public TicketDetail close(Long id, ActionRequest request) {
        User operator = currentUser.require();
        Ticket ticket = requireTicket(id);
        checkVersion(ticket, request.version());
        TicketWorkflow.require(ticket.getStatus(), TicketStatus.VERIFIED);
        TicketStatus from = ticket.getStatus();
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        return saveTransition(ticket, operator, from, "CLOSE", request.comment());
    }

    private TicketDetail saveTransition(
            Ticket ticket,
            User operator,
            TicketStatus from,
            String action,
            String comment
    ) {
        ticketRepository.saveAndFlush(ticket);
        recordTransition(ticket, operator, from, ticket.getStatus(), action, comment);
        eventPublisher.publishEvent(new TicketChangedEvent(ticket.getId(), false));
        return toDetail(ticket, transitionRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    private void recordTransition(
            Ticket ticket,
            User operator,
            TicketStatus from,
            TicketStatus to,
            String action,
            String comment
    ) {
        TicketTransition transition = new TicketTransition();
        transition.setTicket(ticket);
        transition.setOperator(operator);
        transition.setFromStatus(from);
        transition.setToStatus(to);
        transition.setAction(action);
        transition.setComment(StringUtils.hasText(comment) ? comment.trim() : null);
        transition.setCreatedAt(Instant.now());
        transitionRepository.save(transition);
    }

    private Ticket requireTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("问题单不存在"));
    }

    private void requireVisible(Ticket ticket, User user) {
        if (currentUser.permissions(user).contains("ticket:read:all")) {
            return;
        }
        boolean related = ticket.getCreator().getId().equals(user.getId())
                || (ticket.getAssignee() != null && ticket.getAssignee().getId().equals(user.getId()));
        if (!related) {
            throw BusinessException.forbidden("无权查看该问题单");
        }
    }

    private void requireAssignee(Ticket ticket, User operator) {
        if (ticket.getAssignee() == null || !ticket.getAssignee().getId().equals(operator.getId())) {
            throw BusinessException.forbidden("只有当前处理人可以执行此操作");
        }
    }

    private boolean requireModifiable(Ticket ticket, User operator) {
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw BusinessException.forbidden("已关闭问题单仅允许查看");
        }
        Set<String> permissions = currentUser.permissions(operator);
        boolean creator = ticket.getCreator().getId().equals(operator.getId())
                && permissions.contains("ticket:update");
        boolean assignee = ticket.getAssignee() != null
                && ticket.getAssignee().getId().equals(operator.getId())
                && permissions.contains("ticket:process");
        boolean managerCanEditNew = ticket.getStatus() == TicketStatus.NEW
                && permissions.contains("ticket:update:all");
        if (!creator && !assignee && !managerCanEditNew) {
            throw BusinessException.forbidden("当前状态或权限不允许更新问题单");
        }
        return ticket.getStatus() == TicketStatus.NEW && (creator || managerCanEditNew);
    }

    private void checkVersion(Ticket ticket, Long version) {
        if (ticket.getVersion() != version) {
            throw new BusinessException(
                    "CONCURRENT_UPDATE",
                    "问题单已被更新，请刷新后重试",
                    org.springframework.http.HttpStatus.CONFLICT
            );
        }
    }

    private String generateTicketNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return "ISS-" + NUMBER_DATE.format(Instant.now()) + "-" + suffix;
    }

    private TicketSummary toSummary(Ticket ticket) {
        return new TicketSummary(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                toUserSummary(ticket.getCreator()),
                toUserSummary(ticket.getAssignee()),
                toVersionSummary(ticket.getAffectedVersion()),
                toVersionSummary(ticket.getResolvedVersion()),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt()
        );
    }

    private TicketDetail toDetail(Ticket ticket, List<TicketTransition> transitions) {
        return new TicketDetail(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                toUserSummary(ticket.getCreator()),
                toUserSummary(ticket.getAssignee()),
                toVersionSummary(ticket.getAffectedVersion()),
                toVersionSummary(ticket.getResolvedVersion()),
                ticket.getResolution(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getVerifiedAt(),
                ticket.getClosedAt(),
                transitions.stream().map(this::toTransitionView).toList(),
                attachmentService.listViews(ticket.getId())
        );
    }

    private TransitionView toTransitionView(TicketTransition transition) {
        return new TransitionView(
                transition.getId(),
                transition.getFromStatus(),
                transition.getToStatus(),
                transition.getAction(),
                transition.getComment(),
                toUserSummary(transition.getOperator()),
                transition.getCreatedAt()
        );
    }

    private UserSummary toUserSummary(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getUsername(), user.getDisplayName());
    }

    private VersionSummary toVersionSummary(ProductVersion version) {
        return version == null
                ? null
                : new VersionSummary(version.getId(), version.getVersionNo(), version.getName());
    }

    private TicketFilter resolveFilter(
            User user,
            Set<String> permissions,
            TicketScope requestedScope,
            Long requestedCreatorId
    ) {
        boolean canReadAll = permissions.contains("ticket:read:all");
        TicketScope scope = requestedScope == null
                ? (canReadAll ? TicketScope.ALL : TicketScope.RELATED)
                : requestedScope;
        if (!canReadAll) {
            if (scope == TicketScope.MY_CREATED) {
                return new TicketFilter(user.getId(), user.getId());
            }
            if (scope == TicketScope.CREATED_BY
                    && requestedCreatorId != null
                    && requestedCreatorId.equals(user.getId())) {
                return new TicketFilter(user.getId(), user.getId());
            }
            return new TicketFilter(user.getId(), null);
        }
        return switch (scope) {
            case ALL -> new TicketFilter(null, null);
            case RELATED -> new TicketFilter(user.getId(), null);
            case MY_CREATED -> new TicketFilter(null, user.getId());
            case CREATED_BY -> {
                if (requestedCreatorId == null) {
                    throw BusinessException.badRequest("CREATOR_REQUIRED", "请选择创建人");
                }
                yield new TicketFilter(null, requestedCreatorId);
            }
        };
    }

    private record TicketFilter(Long visibilityUserId, Long creatorId) {
    }
}
