package com.example.issuetracker.ticket;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.domain.TicketStatus;

public final class TicketWorkflow {

    private TicketWorkflow() {
    }

    public static void require(TicketStatus current, TicketStatus... allowed) {
        for (TicketStatus status : allowed) {
            if (current == status) {
                return;
            }
        }
        throw BusinessException.badRequest(
                "INVALID_TICKET_STATUS",
                "当前状态 " + current + " 不允许执行此操作"
        );
    }
}

