package org.booklore.model.dto.acquisition;

import org.booklore.model.enums.JobHistoryStatus;

import java.time.Instant;

public record JobHistoryDTO(Long id, Long wantedBookId, Long indexerId, String nzbTitle, Integer confidence, JobHistoryStatus status, Instant attemptedAt) {}
