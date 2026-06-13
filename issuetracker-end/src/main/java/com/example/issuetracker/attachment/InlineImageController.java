package com.example.issuetracker.attachment;

import com.example.issuetracker.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/inline-images")
@RequiredArgsConstructor
public class InlineImageController {

    private static final long MAX_INLINE_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp");

    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InlineImageView upload(@RequestPart("file") MultipartFile file) {
        validateImage(file);
        FileStorageService.StoredFile stored = fileStorageService.storeInlineImage(file);
        return new InlineImageView(
                stored.originalName(),
                "/api/inline-images/" + stored.storageKey()
        );
    }

    @GetMapping("/{storageKey:.+}")
    public ResponseEntity<Resource> view(@PathVariable String storageKey) {
        if (!storageKey.startsWith("inline-")) {
            throw BusinessException.notFound("图片不存在");
        }
        Resource resource = fileStorageService.load(storageKey);
        MediaType contentType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(resource.getFilename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(resource);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_IMAGE", "不能上传空图片");
        }
        if (file.getSize() > MAX_INLINE_IMAGE_SIZE) {
            throw BusinessException.badRequest("IMAGE_TOO_LARGE", "粘贴图片不能超过 5MB");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)
                || file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw BusinessException.badRequest("INVALID_IMAGE_TYPE", "仅支持 PNG、JPG、GIF 和 WebP 图片");
        }
    }

    public record InlineImageView(String originalName, String url) {
    }
}
