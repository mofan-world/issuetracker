package com.example.issuetracker.project;

import com.example.issuetracker.project.ProjectDtos.AddMembersRequest;
import com.example.issuetracker.project.ProjectDtos.CopyMembersRequest;
import com.example.issuetracker.project.ProjectDtos.CreateProjectRequest;
import com.example.issuetracker.project.ProjectDtos.ImportMembersResult;
import com.example.issuetracker.project.ProjectDtos.ProjectMemberView;
import com.example.issuetracker.project.ProjectDtos.ProjectUserOption;
import com.example.issuetracker.project.ProjectDtos.ProjectView;
import com.example.issuetracker.project.ProjectDtos.UpdateProjectRequest;
import com.example.issuetracker.ticket.TicketDtos.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/projects/my")
    public List<ProjectView> myProjects() {
        return projectService.listMyProjects();
    }

    @GetMapping("/projects/{projectId}/users/options")
    public List<ProjectUserOption> projectUsers(
            @PathVariable Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean processorsOnly
    ) {
        return projectService.listProjectUsers(projectId, keyword, processorsOnly);
    }

    @GetMapping("/admin/projects")
    @PreAuthorize("hasAuthority('project:manage')")
    public List<ProjectView> projects() {
        return projectService.listAllProjects();
    }

    @PostMapping("/admin/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:manage')")
    public ProjectView create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(request);
    }

    @PutMapping("/admin/projects/{projectId}")
    @PreAuthorize("hasAuthority('project:manage')")
    public ProjectView update(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(projectId, request);
    }

    @GetMapping("/admin/projects/{projectId}/members")
    @PreAuthorize("hasAuthority('project:manage')")
    public PageResult<ProjectMemberView> members(
            @PathVariable Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return projectService.listMembers(projectId, keyword, page, size);
    }

    @PostMapping("/admin/projects/{projectId}/members")
    @PreAuthorize("hasAuthority('project:manage')")
    public ImportMembersResult addMembers(
            @PathVariable Long projectId,
            @Valid @RequestBody AddMembersRequest request
    ) {
        return projectService.addMembers(projectId, request);
    }

    @DeleteMapping("/admin/projects/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:manage')")
    public void removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.removeMember(projectId, userId);
    }

    @PostMapping("/admin/projects/{projectId}/members/copy")
    @PreAuthorize("hasAuthority('project:manage')")
    public ImportMembersResult copyMembers(
            @PathVariable Long projectId,
            @Valid @RequestBody CopyMembersRequest request
    ) {
        return projectService.copyMembers(projectId, request);
    }

    @PostMapping(
            value = "/admin/projects/{projectId}/members/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('project:manage')")
    public ImportMembersResult importMembers(
            @PathVariable Long projectId,
            @RequestPart("file") MultipartFile file
    ) {
        return projectService.importMembers(projectId, file);
    }
}
