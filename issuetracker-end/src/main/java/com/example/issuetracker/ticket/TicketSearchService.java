package com.example.issuetracker.ticket;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.issuetracker.config.AppProperties;
import com.example.issuetracker.domain.Ticket;
import com.example.issuetracker.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketSearchService {

    private static final String INDEX = "issue-tickets";

    private final ElasticsearchClient client;
    private final TicketRepository ticketRepository;
    private final AppProperties properties;

    public SearchResult search(String keyword, int page, int size) {
        if (!properties.elasticsearch().enabled()) {
            return SearchResult.unavailable();
        }
        try {
            var response = client.search(s -> s
                            .index(INDEX)
                            .from(page * size)
                            .size(size)
                            .query(q -> q.multiMatch(m -> m
                                    .query(keyword)
                                    .fields("ticketNo^3", "title^2", "description", "category"))),
                    TicketSearchDocument.class);
            List<Long> ids = response.hits().hits().stream()
                    .map(hit -> hit.source() == null ? null : hit.source().id())
                    .filter(Objects::nonNull)
                    .toList();
            long total = response.hits().total() == null ? ids.size() : response.hits().total().value();
            return new SearchResult(ids, total, true);
        } catch (IOException | RuntimeException ex) {
            log.warn("Elasticsearch search failed, falling back to PostgreSQL: {}", ex.getMessage());
            return SearchResult.unavailable();
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void synchronize(TicketChangedEvent event) {
        if (!properties.elasticsearch().enabled()) {
            return;
        }
        if (event.deleted()) {
            delete(event.ticketId());
            return;
        }
        ticketRepository.findById(event.ticketId()).ifPresent(this::index);
    }

    private void index(Ticket ticket) {
        TicketSearchDocument document = new TicketSearchDocument(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getProject().getId(),
                ticket.getCreator().getId(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
        try {
            client.index(i -> i.index(INDEX).id(ticket.getId().toString()).document(document));
        } catch (IOException | RuntimeException ex) {
            log.warn("Unable to index ticket {}: {}", ticket.getId(), ex.getMessage());
        }
    }

    private void delete(Long ticketId) {
        try {
            client.delete(d -> d.index(INDEX).id(ticketId.toString()));
        } catch (IOException | RuntimeException ex) {
            log.warn("Unable to remove ticket {} from index: {}", ticketId, ex.getMessage());
        }
    }

    public record SearchResult(List<Long> ids, long total, boolean available) {
        static SearchResult unavailable() {
            return new SearchResult(List.of(), 0, false);
        }
    }
}
