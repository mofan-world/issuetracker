package com.example.issuetracker.attachment;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final AppProperties properties;
    private Path root;
    private Set<String> allowedExtensions;

    @PostConstruct
    void initialize() {
        root = Path.of(properties.storage().root()).toAbsolutePath().normalize();
        allowedExtensions = properties.storage().allowedExtensions().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建附件存储目录: " + root, ex);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_ATTACHMENT", "不能上传空文件");
        }
        if (file.getSize() > properties.storage().maxFileSize().toBytes()) {
            throw new BusinessException(
                    "ATTACHMENT_TOO_LARGE",
                    "单个附件不能超过 " + properties.storage().maxFileSize(),
                    HttpStatus.PAYLOAD_TOO_LARGE
            );
        }
        String originalName = sanitizeName(file.getOriginalFilename());
        String extension = extension(originalName);
        if (!allowedExtensions.contains(extension)) {
            throw BusinessException.badRequest(
                    "ATTACHMENT_TYPE_NOT_ALLOWED",
                    "不支持的附件类型: " + extension
            );
        }
        String storageKey = UUID.randomUUID() + "." + extension;
        Path target = resolve(storageKey);
        try (var input = file.getInputStream()) {
            Files.copy(input, target);
            return new StoredFile(
                    originalName,
                    storageKey,
                    file.getContentType(),
                    file.getSize()
            );
        } catch (IOException ex) {
            throw new BusinessException("ATTACHMENT_SAVE_FAILED", "附件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Resource load(String storageKey) {
        Path file = resolve(storageKey);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw BusinessException.notFound("附件文件不存在");
            }
            return resource;
        } catch (IOException ex) {
            throw BusinessException.notFound("附件文件不存在");
        }
    }

    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
            // Database metadata remains authoritative; orphan cleanup can retry later.
        }
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw BusinessException.badRequest("INVALID_STORAGE_KEY", "非法附件路径");
        }
        return resolved;
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("INVALID_ATTACHMENT_NAME", "附件名称不能为空");
        }
        String normalized = name.replace('\\', '/');
        String result = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (result.isBlank() || result.length() > 255) {
            throw BusinessException.badRequest("INVALID_ATTACHMENT_NAME", "附件名称不合法");
        }
        return result;
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(
            String originalName,
            String storageKey,
            String contentType,
            long fileSize
    ) {
    }
}
