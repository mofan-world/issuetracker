package com.example.issuetracker.security;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.domain.Permission;
import com.example.issuetracker.domain.User;
import com.example.issuetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public User require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw BusinessException.forbidden("请先登录");
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> BusinessException.forbidden("当前用户不存在或已失效"));
    }

    public Set<String> permissions(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}

