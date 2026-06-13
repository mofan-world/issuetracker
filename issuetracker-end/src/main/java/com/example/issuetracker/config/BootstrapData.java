package com.example.issuetracker.config;

import com.example.issuetracker.domain.Role;
import com.example.issuetracker.domain.User;
import com.example.issuetracker.repository.RoleRepository;
import com.example.issuetracker.repository.UserRepository;
import com.example.issuetracker.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class BootstrapData implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final ProjectService projectService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = properties.bootstrap().adminUsername().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing"));
        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(properties.bootstrap().adminEmail().toLowerCase(Locale.ROOT));
        admin.setDisplayName("系统管理员");
        admin.setPasswordHash(passwordEncoder.encode(properties.bootstrap().adminPassword()));
        admin.setEnabled(true);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
        projectService.addToDefaultProject(admin);
    }
}

