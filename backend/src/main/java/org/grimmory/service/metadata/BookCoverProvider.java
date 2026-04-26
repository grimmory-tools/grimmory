package org.grimmory.service.metadata;

import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.CoverFetchRequest;

import reactor.core.publisher.Flux;

public interface BookCoverProvider {
    Flux<CoverImage> getCovers(CoverFetchRequest request);
}

