package com.example.issuetracker.project;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.domain.Permission;
import com.example.issuetracker.domain.Project;
import com.example.issuetracker.domain.ProjectMember;
import com.example.issuetracker.domain.Role;
import com.example.issuetracker.domain.User;
import com.example.issuetracker.project.ProjectDtos.AddMembersRequest;
import com.example.issuetracker.project.ProjectDtos.CopyMembersRequest;
import com.example.issuetracker.project.ProjectDtos.CreateProjectRequest;
import com.example.issuetracker.project.ProjectDtos.ImportMembersResult;
import com.example.issuetracker.project.ProjectDtos.ProjectMemberView;
import com.example.issuetracker.project.ProjectDtos.ProjectUserOption;
import com.example.issuetracker.project.ProjectDtos.ProjectView;
import com.example.issuetracker.project.ProjectDtos.UpdateProjectRequest;
import com.example.issuetracker.repository.ProjectMemberRepository;
import com.example.issuetracker.repository.ProjectRepository;
import com.example.issuetracker.repository.UserRepository;
import com.example.issuetracker.security.CurrentUser;
import com.example.issuetracker.ticket.TicketDtos.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final long DEFAULT_PROJECT_ID = 1L;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final XlsxUserReader xlsxUserReader;

    @Transactional(readOnly = true)
    public List<ProjectView> listMyProjects() {
        User user = currentUser.require();
        List<Project> projects = canManage(user)
                ? projectRepository.findByEnabledTrueOrderByNameAsc()
                : memberRepository.findEnabledProjectsByUserId(user.getId());
        return projects.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectView> listAllProjects() {
        return projectRepository.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    @Transactional
    public ProjectView create(CreateProjectRequest request) {
        String code = normalizeCode(request.code());
        if (projectRepository.existsByCodeIgnoreCase(code)) {
            throw BusinessException.badRequest("PROJECT_CODE_EXISTS", "项目编码已存在");
        }
        Project project = new Project();
        project.setCode(code);
        project.setName(request.name().trim());
        project.setDescription(trimToNull(request.description()));
        project.setEnabled(request.enabled());
        project.setCreatedBy(currentUser.require());
        return toView(projectRepository.save(project));
    }

    @Transactional
    public ProjectView update(Long projectId, UpdateProjectRequest request) {
        Project project = requireProject(projectId);
        String code = normalizeCode(request.code());
        if (projectRepository.existsByCodeIgnoreCaseAndIdNot(code, projectId)) {
            throw BusinessException.badRequest("PROJECT_CODE_EXISTS", "项目编码已存在");
        }
        project.setCode(code);
        project.setName(request.name().trim());
        project.setDescription(trimToNull(request.description()));
        project.setEnabled(request.enabled());
        return toView(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public PageResult<ProjectMemberView> listMembers(
            Long projectId,
            String keyword,
            int page,
            int size
    ) {
        requireProject(projectId);
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100))
        );
        var result = memberRepository.searchMembers(
                projectId,
                keyword == null ? "" : keyword.trim(),
                pageable
        );
        return new PageResult<>(
                result.getContent().stream().map(this::toMemberView).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public ImportMembersResult addMembers(Long projectId, AddMembersRequest request) {
        Project project = requireProject(projectId);
        List<User> users = userRepository.findAllById(request.userIds()).stream()
                .filter(user -> !user.isDeleted() && user.isEnabled())
                .toList();
        if (users.size() != request.userIds().size()) {
            throw BusinessException.badRequest("INVALID_PROJECT_MEMBER", "包含不存在、已删除或已禁用的用户");
        }
        int added = addUsers(project, users);
        return new ImportMembersResult(added, users.size() - added, List.of());
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        requireProject(projectId);
        memberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    @Transactional
    public ImportMembersResult copyMembers(Long projectId, CopyMembersRequest request) {
        requireProject(projectId);
        requireProject(request.sourceProjectId());
        if (projectId.equals(request.sourceProjectId())) {
            throw BusinessException.badRequest("SAME_PROJECT", "来源项目不能与目标项目相同");
        }
        long before = memberRepository.countByProjectId(projectId);
        memberRepository.copyMembers(request.sourceProjectId(), projectId);
        long after = memberRepository.countByProjectId(projectId);
        int sourceCount = Math.toIntExact(memberRepository.countByProjectId(request.sourceProjectId()));
        int added = Math.toIntExact(after - before);
        return new ImportMembersResult(added, Math.max(0, sourceCount - added), List.of());
    }

    @Transactional
    public ImportMembersResult importMembers(Long projectId, MultipartFile file) {
        Project project = requireProject(projectId);
        List<XlsxUserReader.UserIdentifier> identifiers = xlsxUserReader.read(file);
        Set<String> usernames = new HashSet<>();
        Set<String> emails = new HashSet<>();
        identifiers.forEach(identifier -> {
            if (!identifier.username().isBlank()) usernames.add(identifier.username());
            if (!identifier.email().isBlank()) emails.add(identifier.email());
        });

        Map<String, User> byUsername = new HashMap<>();
        if (!usernames.isEmpty()) {
            userRepository.findByUsernameInAndDeletedFalse(usernames)
                    .forEach(user -> byUsername.put(user.getUsername().toLowerCase(Locale.ROOT), user));
        }
        Map<String, User> byEmail = new HashMap<>();
        if (!emails.isEmpty()) {
            userRepository.findByEmailInAndDeletedFalse(emails)
                    .forEach(user -> byEmail.put(user.getEmail().toLowerCase(Locale.ROOT), user));
        }

        Set<User> users = new LinkedHashSet<>();
        List<String> notFound = new ArrayList<>();
        for (XlsxUserReader.UserIdentifier identifier : identifiers) {
            User user = byUsername.get(identifier.username());
            if (user == null) user = byEmail.get(identifier.email());
            if (user == null || !user.isEnabled()) {
                notFound.add(!identifier.username().isBlank() ? identifier.username() : identifier.email());
            } else {
                users.add(user);
            }
        }
        int added = addUsers(project, List.copyOf(users));
        int ignored = Math.max(0, identifiers.size() - notFound.size() - added);
        return new ImportMembersResult(added, ignored, notFound.stream().distinct().limit(200).toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectUserOption> listProjectUsers(
            Long projectId,
            String keyword,
            boolean processorsOnly
    ) {
        User operator = currentUser.require();
        Project project = requireAccessibleProject(projectId, operator);
        var page = memberRepository.searchMembers(
                project.getId(),
                keyword == null ? "" : keyword.trim(),
                PageRequest.of(0, 100)
        );
        return page.getContent().stream()
                .map(ProjectMember::getUser)
                .filter(User::isEnabled)
                .filter(user -> !processorsOnly || hasPermission(user, "ticket:process"))
                .map(user -> new ProjectUserOption(user.getId(), user.getUsername(), user.getDisplayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Project resolveAccessibleProject(Long requestedProjectId, User user) {
        if (requestedProjectId != null) {
            return requireAccessibleProject(requestedProjectId, user);
        }
        List<Project> projects = canManage(user)
                ? projectRepository.findByEnabledTrueOrderByNameAsc()
                : memberRepository.findEnabledProjectsByUserId(user.getId());
        if (projects.isEmpty()) {
            throw BusinessException.forbidden("当前用户尚未加入任何可用项目");
        }
        return projects.get(0);
    }

    @Transactional(readOnly = true)
    public Project requireAccessibleProject(Long projectId, User user) {
        Project project = requireProject(projectId);
        if (!project.isEnabled()) {
            throw BusinessException.forbidden("项目已停用");
        }
        if (!canManage(user) && !memberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw BusinessException.forbidden("无权访问该项目");
        }
        return project;
    }

    @Transactional(readOnly = true)
    public void requireProjectMember(Long projectId, Long userId) {
        if (!memberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw BusinessException.badRequest("USER_NOT_IN_PROJECT", "所选用户未加入当前项目");
        }
    }

    @Transactional
    public void addToDefaultProject(User user) {
        Project project = projectRepository.findById(DEFAULT_PROJECT_ID)
                .orElseThrow(() -> new IllegalStateException("DEFAULT project is missing"));
        addUsers(project, List.of(user));
    }

    private int addUsers(Project project, List<User> users) {
        if (users.isEmpty()) return 0;
        Set<Long> candidateIds = users.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> existing = memberRepository.findByProjectIdAndUserIdIn(project.getId(), candidateIds)
                .stream()
                .map(member -> member.getUser().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<ProjectMember> additions = users.stream()
                .filter(user -> !existing.contains(user.getId()))
                .map(user -> {
                    ProjectMember member = new ProjectMember();
                    member.setProject(project);
                    member.setUser(user);
                    return member;
                })
                .toList();
        memberRepository.saveAll(additions);
        return additions.size();
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.notFound("项目不存在"));
    }

    private boolean canManage(User user) {
        return currentUser.permissions(user).contains("project:manage");
    }

    private boolean hasPermission(User user, String permission) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .anyMatch(permission::equals);
    }

    private ProjectView toView(Project project) {
        return new ProjectView(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getDescription(),
                project.isEnabled(),
                memberRepository.countByProjectId(project.getId()),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private ProjectMemberView toMemberView(ProjectMember member) {
        User user = member.getUser();
        return new ProjectMemberView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRoles().stream().map(Role::getCode).sorted().toList(),
                member.getCreatedAt()
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
