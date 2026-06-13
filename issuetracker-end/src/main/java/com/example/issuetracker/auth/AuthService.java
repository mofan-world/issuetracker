package com.example.issuetracker.auth;

import com.example.issuetracker.auth.AuthDtos.LoginRequest;
import com.example.issuetracker.auth.AuthDtos.RefreshRequest;
import com.example.issuetracker.auth.AuthDtos.RegisterRequest;
import com.example.issuetracker.auth.AuthDtos.TokenResponse;
import com.example.issuetracker.auth.AuthDtos.UserProfile;
import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.domain.Permission;
import com.example.issuetracker.domain.Role;
import com.example.issuetracker.domain.User;
import com.example.issuetracker.repository.RoleRepository;
import com.example.issuetracker.repository.UserRepository;
import com.example.issuetracker.security.CurrentUser;
import com.example.issuetracker.security.JwtService;
import com.example.issuetracker.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUser currentUser;
    private final ProjectService projectService;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw BusinessException.badRequest("USERNAME_EXISTS", "用户名已存在");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.badRequest("EMAIL_EXISTS", "邮箱已注册");
        }
        Role defaultRole = roleRepository.findByCode("USER")
                .orElseThrow(() -> new IllegalStateException("默认角色 USER 不存在"));
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEnabled(true);
        user.getRoles().add(defaultRole);
        userRepository.save(user);
        projectService.addToDefaultProject(user);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username().trim(),
                            request.password()
                    )
            );
        } catch (AuthenticationException ex) {
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findByUsernameIgnoreCaseAndDeletedFalse(request.username().trim())
                .orElseThrow(() -> new BusinessException(
                        "INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED));
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        Long userId = refreshTokenService.consume(request.refreshToken());
        User user = userRepository.findWithRolesById(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new BusinessException(
                        "USER_DISABLED", "用户不存在或已禁用", HttpStatus.UNAUTHORIZED));
        return issueTokens(user);
    }

    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional(readOnly = true)
    public UserProfile me() {
        return toProfile(currentUser.require());
    }

    private TokenResponse issueTokens(User user) {
        JwtService.AccessToken accessToken = jwtService.createAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        return new TokenResponse(
                accessToken.value(),
                refreshToken,
                accessToken.expiresAt(),
                toProfile(user)
        );
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRoles().stream().map(Role::getCode).sorted().toList(),
                user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .map(Permission::getCode)
                        .distinct()
                        .sorted()
                        .toList()
        );
    }
}

