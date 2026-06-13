package com.example.issuetracker.version;

import com.example.issuetracker.ticket.TicketDtos.PageResult;
import com.example.issuetracker.version.VersionDtos.SaveVersionRequest;
import com.example.issuetracker.version.VersionDtos.VersionOption;
import com.example.issuetracker.version.VersionDtos.VersionView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @GetMapping("/options")
    public List<VersionOption> options() {
        return versionService.options();
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('version:manage')")
    public List<VersionView> tree() {
        return versionService.tree();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('version:manage')")
    public PageResult<VersionView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return versionService.list(keyword, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('version:manage')")
    public VersionView create(@Valid @RequestBody SaveVersionRequest request) {
        return versionService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('version:manage')")
    public VersionView update(@PathVariable Long id, @Valid @RequestBody SaveVersionRequest request) {
        return versionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('version:manage')")
    public void delete(@PathVariable Long id) {
        versionService.delete(id);
    }
}
