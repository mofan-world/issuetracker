package com.example.issuetracker.admin;

import com.example.issuetracker.admin.AdminDtos.RoleView;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResult<UserView> listUsers(String keyword, int page, int size) {
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        String query = keyword == null ? "" : keyword.trim();
        var result = userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                query, query, pageable);
        return new PageResult<>(
                result.getContent().stream().map(this::toUserView).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "roles", key = "'all'")
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
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        List<Role> roles = roleRepository.findAllById(request.roleIds());
        if (roles.size() != request.roleIds().size()) {
            throw BusinessException.badRequest("INVALID_ROLE", "包含不存在的角色");
        }
        User operator = currentUser.require();
        boolean keepsAdmin = roles.stream().anyMatch(role -> "ADMIN".equals(role.getCode()));
        if (operator.getId().equals(userId) && !keepsAdmin) {
            throw BusinessException.badRequest("SELF_LOCKOUT", "不能移除自己的管理员角色");
        }
        user.setRoles(new HashSet<>(roles));
        return toUserView(userRepository.save(user));
    }

    @Transactional
    public UserView updateEnabled(Long userId, UpdateEnabledRequest request) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
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
        return userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                        query, query, pageable).stream()
                .filter(User::isEnabled)
                .filter(this::canProcess)
                .map(user -> new UserOption(user.getId(), user.getUsername(), user.getDisplayName()))
                .toList();
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
