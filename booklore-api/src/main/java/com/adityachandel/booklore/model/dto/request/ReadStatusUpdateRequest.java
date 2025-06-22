package com.adityachandel.booklore.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReadStatusUpdateRequest(@NotBlank String status) {}
