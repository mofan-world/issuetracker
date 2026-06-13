package com.example.issuetracker.ticket;

import com.example.issuetracker.attachment.TicketAttachmentService;
import com.example.issuetracker.domain.TicketPriority;
import com.example.issuetracker.domain.TicketStatus;
import com.example.issuetracker.domain.TicketScope;
import com.example.issuetracker.ticket.TicketDtos.ActionRequest;
import com.example.issuetracker.ticket.TicketDtos.AssignRequest;
import com.example.issuetracker.ticket.TicketDtos.CreateTicketRequest;
import com.example.issuetracker.ticket.TicketDtos.PageResult;
import com.example.issuetracker.ticket.TicketDtos.ResolveRequest;
import com.example.issuetracker.ticket.TicketDtos.TicketDetail;
import com.example.issuetracker.ticket.TicketDtos.TicketSummary;
import com.example.issuetracker.ticket.TicketDtos.UpdateTicketRequest;
import com.example.issuetracker.ticket.TicketDtos.VerifyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketAttachmentService attachmentService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ticket:create')")
    public TicketDetail create(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.create(request, List.of());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ticket:create')")
    public TicketDetail createWithAttachments(
            @Valid @RequestPart("request") CreateTicketRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ticketService.create(request, files);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ticket:read:own', 'ticket:read:all')")
    public PageResult<TicketSummary> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketScope scope,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ticketService.list(keyword, status, priority, scope, creatorId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ticket:read:own', 'ticket:read:all')")
    public TicketDetail get(@PathVariable Long id) {
        return ticketService.get(id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ticket:update', 'ticket:update:all')")
    public TicketDetail update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request
    ) {
        return ticketService.update(id, request, List.of());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ticket:update', 'ticket:update:all')")
    public TicketDetail updateWithAttachments(
            @PathVariable Long id,
            @Valid @RequestPart("request") UpdateTicketRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ticketService.update(id, request, files);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ticket:update', 'ticket:update:all', 'ticket:process')")
    public List<TicketDtos.AttachmentView> uploadAttachments(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return attachmentService.upload(id, files);
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        TicketAttachmentService.DownloadFile file = attachmentService.download(attachmentId);
        MediaType contentType;
        try {
            contentType = file.contentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(file.contentType());
        } catch (IllegalArgumentException ex) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(file.fileSize())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(file.resource());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable Long attachmentId) {
        attachmentService.delete(attachmentId);
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

