package com.example.issuetracker.ticket;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.domain.TicketStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketWorkflowTest {

    @Test
    void allowsListedStatus() {
        assertThatCode(() -> TicketWorkflow.require(
                TicketStatus.ASSIGNED,
                TicketStatus.NEW,
                TicketStatus.ASSIGNED
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnlistedStatus() {
        assertThatThrownBy(() -> TicketWorkflow.require(
                TicketStatus.CLOSED,
                TicketStatus.VERIFIED
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CLOSED");
    }
}

