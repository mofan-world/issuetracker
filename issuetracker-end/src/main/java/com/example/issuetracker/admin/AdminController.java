package com.example.issuetracker.admin;

import com.example.issuetracker.admin.AdminDtos.RoleView;
import com.example.issuetracker.admin.AdminDtos.CreateUserRequest;
import com.example.issuetracker.admin.AdminDtos.UpdateUserRequest;
import com.example.issuetracker.admin.AdminDtos.UpdateEnabledRequest;
import com.example.issuetracker.admin.AdminDtos.UpdateRolesRequest;
import com.example.issuetracker.admin.AdminDtos.UserOption;
import com.example.issuetracker.admin.AdminDtos.UserView;
import com.example.issuetracker.ticket.TicketDtos.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasAuthority('user:manage')")
    public PageResult<UserView> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminService.listUsers(keyword, page, size);
    }

    @GetMapping("/api/admin/users/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public UserView user(@PathVariable Long id) {
        return adminService.getUser(id);
    }

    @PostMapping("/api/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user:manage')")
    public UserView createUser(@Valid @RequestBody CreateUserRequest request) {
        return adminService.createUser(request);
    }

    @PutMapping("/api/admin/users/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public UserView updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return adminService.updateUser(id, request);
    }

    @DeleteMapping("/api/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('user:manage')")
    public void deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
    }

    @GetMapping("/api/admin/roles")
    @PreAuthorize("hasAuthority('user:manage')")
    public List<RoleView> roles() {
        return adminService.listRoles();
    }

    @PutMapping("/api/admin/users/{id}/roles")
    @PreAuthorize("hasAuthority('user:manage')")
    public UserView updateRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRolesRequest request
    ) {
        return adminService.updateRoles(id, request);
    }

    @PatchMapping("/api/admin/users/{id}/enabled")
    @PreAuthorize("hasAuthority('user:manage')")
    public UserView updateEnabled(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        return adminService.updateEnabled(id, request);
    }

    @GetMapping("/api/users/assignees")
    @PreAuthorize("hasAuthority('ticket:assign')")
    public List<UserOption> assignees(@RequestParam(required = false) String keyword) {
        return adminService.listAssignees(keyword);
    }
}

