package com.example.issuetracker.attachment;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.config.AppProperties;
import com.example.issuetracker.domain.Ticket;
import com.example.issuetracker.domain.TicketAttachment;
import com.example.issuetracker.domain.TicketStatus;
import com.example.issuetracker.domain.User;
import com.example.issuetracker.repository.TicketAttachmentRepository;
import com.example.issuetracker.repository.TicketRepository;
import com.example.issuetracker.security.CurrentUser;
import com.example.issuetracker.ticket.TicketDtos.AttachmentView;
import com.example.issuetracker.ticket.TicketDtos.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketAttachmentService {

    private final TicketAttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final FileStorageService fileStorageService;
    private final CurrentUser currentUser;
    private final AppProperties properties;

    @Transactional
    public List<AttachmentView> upload(Long ticketId, List<MultipartFile> files) {
        User operator = currentUser.require();
        Ticket ticket = requireTicket(ticketId);
        requireModifiable(ticket, operator);
        store(ticket, operator, files);
        return listViews(ticketId);
    }

    public void store(Ticket ticket, User uploader, List<MultipartFile> files) {
        List<MultipartFile> actualFiles = files == null
                ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (actualFiles.isEmpty()) {
            return;
        }
        long currentCount = attachmentRepository.countByTicketId(ticket.getId());
        if (currentCount + actualFiles.size() > properties.storage().maxFilesPerTicket()) {
            throw BusinessException.badRequest(
                    "TOO_MANY_ATTACHMENTS",
                    "每张问题单最多上传 " + properties.storage().maxFilesPerTicket() + " 个附件"
            );
        }

        List<String> storedKeys = new ArrayList<>();
        try {
            for (MultipartFile file : actualFiles) {
                FileStorageService.StoredFile stored = fileStorageService.store(file);
                storedKeys.add(stored.storageKey());
                registerRollbackCleanup(stored.storageKey());
                TicketAttachment attachment = new TicketAttachment();
                attachment.setTicket(ticket);
                attachment.setUploader(uploader);
                attachment.setOriginalName(stored.originalName());
                attachment.setStorageKey(stored.storageKey());
                attachment.setContentType(stored.contentType());
                attachment.setFileSize(stored.fileSize());
                attachment.setCreatedAt(Instant.now());
                attachmentRepository.save(attachment);
            }
        } catch (RuntimeException ex) {
            storedKeys.forEach(fileStorageService::deleteQuietly);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DownloadFile download(Long attachmentId) {
        TicketAttachment attachment = requireAttachment(attachmentId);
        requireVisible(attachment.getTicket(), currentUser.require());
        Resource resource = fileStorageService.load(attachment.getStorageKey());
        return new DownloadFile(
                resource,
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getFileSize()
        );
    }

    @Transactional
    public void delete(Long attachmentId) {
        TicketAttachment attachment = requireAttachment(attachmentId);
        User operator = currentUser.require();
        Set<String> permissions = currentUser.permissions(operator);
        boolean owner = attachment.getUploader().getId().equals(operator.getId());
        boolean creator = attachment.getTicket().getCreator().getId().equals(operator.getId());
        if (!permissions.contains("attachment:delete:all") && !owner && !creator) {
            throw BusinessException.forbidden("无权删除该附件");
        }
        String storageKey = attachment.getStorageKey();
        attachmentRepository.delete(attachment);
        registerCommitCleanup(storageKey);
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> listViews(Long ticketId) {
        return attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(this::toView)
                .toList();
    }

    private Ticket requireTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("问题单不存在"));
    }

    private TicketAttachment requireAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> BusinessException.notFound("附件不存在"));
    }

    private void requireVisible(Ticket ticket, User user) {
        if (currentUser.permissions(user).contains("ticket:read:all")) {
            return;
        }
        boolean related = ticket.getCreator().getId().equals(user.getId())
                || (ticket.getAssignee() != null && ticket.getAssignee().getId().equals(user.getId()));
        if (!related) {
            throw BusinessException.forbidden("无权访问该附件");
        }
    }

    private void requireModifiable(Ticket ticket, User user) {
        Set<String> permissions = currentUser.permissions(user);
        boolean manager = permissions.contains("ticket:update:all");
        boolean creatorCanEdit = permissions.contains("ticket:update")
                && ticket.getCreator().getId().equals(user.getId())
                && (ticket.getStatus() == TicketStatus.NEW || ticket.getStatus() == TicketStatus.ASSIGNED);
        boolean assigneeCanAttach = permissions.contains("ticket:process")
                && ticket.getAssignee() != null
                && ticket.getAssignee().getId().equals(user.getId())
                && (ticket.getStatus() == TicketStatus.ASSIGNED || ticket.getStatus() == TicketStatus.IN_PROGRESS);
        if (!manager && !creatorCanEdit && !assigneeCanAttach) {
            throw BusinessException.forbidden("当前状态或权限不允许更新附件");
        }
    }

    private AttachmentView toView(TicketAttachment attachment) {
        User uploader = attachment.getUploader();
        return new AttachmentView(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                new UserSummary(uploader.getId(), uploader.getUsername(), uploader.getDisplayName()),
                attachment.getCreatedAt()
        );
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    fileStorageService.deleteQuietly(storageKey);
                }
            }
        });
    }

    private void registerCommitCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStorageService.deleteQuietly(storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.deleteQuietly(storageKey);
            }
        });
    }

    public record DownloadFile(Resource resource, String filename, String contentType, long fileSize) {
    }
}
