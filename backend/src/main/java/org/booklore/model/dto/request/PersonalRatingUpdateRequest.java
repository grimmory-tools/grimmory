package org.booklore.model.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record PersonalRatingUpdateRequest(@Size(max = 500) List<Long> ids, Integer rating) {
}
