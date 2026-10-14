package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import org.booklore.browse.BrowsePager;
import org.booklore.model.dto.AuthorSummary;
import org.booklore.model.dto.Book;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BrowseConfig {

    @Bean
    BrowseService<BookEntity, Book> bookBrowse(BookFacetRegistry facets, BookSortRegistry sorts, BookPageFetcher pages,
                                               BrowseScopeFactory scopeFactory, BrowsePager pager, EntityManager entityManager) {
        return new BrowseService<>(new BrowseEntity<>(
                BookEntity.class,
                "/api/v1/books/page",
                "/api/v1/books/facets",
                BrowseScope::visibleBooks,
                BookSearchSpecification::matching,
                facets.registry(),
                scope -> sorts.registry(),
                pages), scopeFactory, pager, entityManager);
    }

    @Bean
    BrowseService<AuthorEntity, AuthorSummary> authorBrowse(AuthorFacetRegistry facets, AuthorSortRegistry sorts, AuthorPageFetcher pages,
                                                            AuthorVisibleBooks visibleBooks, BrowseScopeFactory scopeFactory,
                                                            BrowsePager pager, EntityManager entityManager) {
        return new BrowseService<>(new BrowseEntity<>(
                AuthorEntity.class,
                "/api/v1/authors/page",
                "/api/v1/authors/facets",
                visibleBooks::visibleAuthors,
                AuthorSearchSpecification::matching,
                facets.registry(),
                sorts::registry,
                pages), scopeFactory, pager, entityManager);
    }
}
