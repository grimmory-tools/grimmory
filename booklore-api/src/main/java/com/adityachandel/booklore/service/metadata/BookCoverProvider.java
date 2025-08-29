package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.CoverImage;
import com.adityachandel.booklore.model.dto.request.CoverFetchRequest;

import java.util.List;

public interface BookCoverProvider {
    List<CoverImage> getCovers(CoverFetchRequest request);
}

