package com.example.issuetracker.admin;

import com.example.issuetracker.admin.AdminDtos.RoleView;
import com.example.issuetracker.admin.AdminDtos.CreateUserRequest;
import com.example.issuetracker.admin.AdminDtos.UpdateUserRequest;
import com.example.issuetracker.admin.AdminDtos.UpdateEnabledRequest;
import com.example.issuetracker.admin.AdminDtos.UpdateRolesRequest;
import com.example.issuetracker.admin.AdminDtos.UserOption;
import com.example.issuetracker.admin.AdminDtos.UserView;
import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.domain.Permission;
import com.example.issuetracker.domain.Role;
import com.example.issuetracker.domain.User;
import com.example.issuetracker.repository.RoleRepository;
import com.example.issuetracker.repository.UserRepository;
import com.example.issuetracker.security.CurrentUser;
import com.example.issuetracker.ticket.TicketDtos.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResult<UserView> listUsers(String keyword, int page, int size) {
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        String query = keyword == null ? "" : keyword.trim();
        var result = userRepository.searchActiveUsers(query, pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toUserView).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public UserView getUser(Long userId) {
        return toUserView(requireUser(userId));
    }

    @Transactional
    public UserView createUser(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        requireUnique(username, email, null);
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(request.enabled());
        user.setRoles(requireRoles(request.roleIds()));
        return toUserView(userRepository.save(user));
    }

    @Transactional
    public UserView updateUser(Long userId, UpdateUserRequest request) {
        User user = requireUser(userId);
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        requireUnique(username, email, userId);
        Set<Role> roles = requireRoles(request.roleIds());
        requireNoSelfLockout(user, request.enabled(), roles);
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setEnabled(request.enabled());
        user.setRoles(roles);
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toUserView(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = requireUser(userId);
        if (currentUser.require().getId().equals(userId)) {
            throw BusinessException.badRequest("SELF_LOCKOUT", "不能删除当前登录账号");
        }
        user.setEnabled(false);
        user.setDeleted(true);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "roles", key = "'v3:all'")
    public List<RoleView> listRoles() {
        return roleRepository.findAll().stream()
                .map(role -> new RoleView(
                        role.getId(),
                        role.getCode(),
                        role.getName(),
                        role.getPermissions().stream().map(Permission::getCode).sorted().toList()
                ))
                .toList();
    }

    @Transactional
    public UserView updateRoles(Long userId, UpdateRolesRequest request) {
        User user = requireUser(userId);
        Set<Role> roles = requireRoles(request.roleIds());
        User operator = currentUser.require();
        boolean keepsAdmin = roles.stream().anyMatch(role -> "ADMIN".equals(role.getCode()));
        if (operator.getId().equals(userId) && !keepsAdmin) {
            throw BusinessException.badRequest("SELF_LOCKOUT", "不能移除自己的管理员角色");
        }
        user.setRoles(roles);
        return toUserView(userRepository.save(user));
    }

    @Transactional
    public UserView updateEnabled(Long userId, UpdateEnabledRequest request) {
        User user = requireUser(userId);
        if (!request.enabled() && currentUser.require().getId().equals(userId)) {
            throw BusinessException.badRequest("SELF_LOCKOUT", "不能禁用当前登录账号");
        }
        user.setEnabled(request.enabled());
        return toUserView(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserOption> listAssignees(String keyword) {
        var pageable = PageRequest.of(0, 50, Sort.by("displayName"));
        String query = keyword == null ? "" : keyword.trim();
        return userRepository.searchActiveUsers(query, pageable).stream()
                .filter(User::isEnabled)
                .filter(this::canProcess)
                .map(user -> new UserOption(user.getId(), user.getUsername(), user.getDisplayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserOption> listUserOptions(String keyword) {
        var pageable = PageRequest.of(0, 100, Sort.by("displayName"));
        String query = keyword == null ? "" : keyword.trim();
        return userRepository.searchActiveUsers(query, pageable).stream()
                .filter(User::isEnabled)
                .map(user -> new UserOption(user.getId(), user.getUsername(), user.getDisplayName()))
                .toList();
    }

    private User requireUser(Long userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    private Set<Role> requireRoles(Set<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.badRequest("INVALID_ROLE", "包含不存在的角色");
        }
        return new HashSet<>(roles);
    }

    private void requireUnique(String username, String email, Long userId) {
        boolean usernameExists = userId == null
                ? userRepository.existsByUsernameIgnoreCase(username)
                : userRepository.existsByUsernameIgnoreCaseAndIdNot(username, userId);
        boolean emailExists = userId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId);
        if (usernameExists) {
            throw BusinessException.badRequest("USERNAME_EXISTS", "用户名已存在");
        }
        if (emailExists) {
            throw BusinessException.badRequest("EMAIL_EXISTS", "邮箱已存在");
        }
    }

    private void requireNoSelfLockout(User user, boolean enabled, Set<Role> roles) {
        if (!currentUser.require().getId().equals(user.getId())) {
            return;
        }
        if (!enabled || roles.stream().noneMatch(role -> "ADMIN".equals(role.getCode()))) {
            throw BusinessException.badRequest("SELF_LOCKOUT", "不能禁用自己或移除自己的管理员角色");
        }
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean canProcess(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .anyMatch("ticket:process"::equals);
    }

    private UserView toUserView(User user) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEnabled(),
                user.getRoles().stream().map(Role::getCode).sorted().toList(),
                user.getCreatedAt()
        );
    }
}
