package org.booklore.service.metadata;

import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.CoverFetchRequest;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public interface BookCoverProvider {
    void streamCovers(CoverFetchRequest request, Consumer<CoverImage> onResult, BooleanSupplier isCancelled);
}


