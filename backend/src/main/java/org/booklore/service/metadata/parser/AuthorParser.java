package org.booklore.service.metadata.parser;

import org.booklore.model.dto.AuthorSearchResult;

import java.util.List;

public interface AuthorParser {

    List<AuthorSearchResult> searchAuthors(String name, String region);

    AuthorSearchResult getAuthorByAsin(String asin, String region);

    AuthorSearchResult quickSearch(String name, String region);
}
