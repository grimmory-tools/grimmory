package org.grimmory.service.metadata;

import org.grimmory.model.dto.CoverImage;
import org.grimmory.model.dto.request.CoverFetchRequest;

import reactor.core.publisher.Flux;

public interface BookCoverProvider {
    Flux<CoverImage> getCovers(CoverFetchRequest request);
}

