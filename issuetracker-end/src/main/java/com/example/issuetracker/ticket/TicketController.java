package com.example.issuetracker.ticket;

import com.example.issuetracker.domain.TicketPriority;
import com.example.issuetracker.domain.TicketStatus;
import com.example.issuetracker.ticket.TicketDtos.ActionRequest;
import com.example.issuetracker.ticket.TicketDtos.AssignRequest;
import com.example.issuetracker.ticket.TicketDtos.CreateTicketRequest;
import com.example.issuetracker.ticket.TicketDtos.PageResult;
import com.example.issuetracker.ticket.TicketDtos.ResolveRequest;
import com.example.issuetracker.ticket.TicketDtos.TicketDetail;
import com.example.issuetracker.ticket.TicketDtos.TicketSummary;
import com.example.issuetracker.ticket.TicketDtos.VerifyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ticket:create')")
    public TicketDetail create(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ticket:read:own', 'ticket:read:all')")
    public PageResult<TicketSummary> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ticketService.list(keyword, status, priority, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ticket:read:own', 'ticket:read:all')")
    public TicketDetail get(@PathVariable Long id) {
        return ticketService.get(id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('ticket:assign')")
    public TicketDetail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return ticketService.assign(id, request);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('ticket:process')")
    public TicketDetail start(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
        return ticketService.start(id, request);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('ticket:process')")
    public TicketDetail resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request) {
        return ticketService.resolve(id, request);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('ticket:verify')")
    public TicketDetail verify(@PathVariable Long id, @Valid @RequestBody VerifyRequest request) {
        return ticketService.verify(id, request);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ticket:close')")
    public TicketDetail close(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
        return ticketService.close(id, request);
    }
}

