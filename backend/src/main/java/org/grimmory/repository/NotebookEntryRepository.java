package org.grimmory.repository;

import org.grimmory.model.entity.NotebookEntryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface NotebookEntryRepository extends Repository<NotebookEntryView, Long> {

    interface EntryProjection {
        Long getId();
        String getType();
        Long getBookId();
        String getBookTitle();
        String getText();
        String getNote();
        String getColor();
        String getStyle();
        String getChapterTitle();
        String getPrimaryBookType();
        LocalDateTime getCreatedAt();
        LocalDateTime getUpdatedAt();
    }

    interface BookProjection {
        Long getBookId();
        String getBookTitle();
    }

    interface BookWithCountProjection {
        Long getBookId();
        String getBookTitle();
        int getNoteCount();
        Instant getCoverUpdatedOn();
    }

    @Query(value = """
            SELECT ne.originalId as id,
                   ne.entryType as type,
                   ne.bookId as bookId,
                   ne.bookTitle as bookTitle,
                   ne.textContent as text,
                   ne.noteContent as note,
                   ne.color as color,
                   ne.style as style,
                   ne.chapterTitle as chapterTitle,
                   ne.primaryBookType as primaryBookType,
                   ne.createdAt as createdAt,
                   ne.updatedAt as updatedAt
            FROM NotebookEntryView ne
            WHERE ne.userId = :userId
            AND ne.entryType IN :types
            AND (:bookId IS NULL OR ne.bookId = :bookId)
            AND (:search IS NULL
                 OR ne.textContent LIKE :search ESCAPE '\\'
                 OR ne.noteContent LIKE :search ESCAPE '\\'
                 OR ne.bookTitle LIKE :search ESCAPE '\\'
                 OR ne.chapterTitle LIKE :search ESCAPE '\\')
            """,
           countQuery = """
            SELECT count(ne)
            FROM NotebookEntryView ne
            WHERE ne.userId = :userId
            AND ne.entryType IN :types
            AND (:bookId IS NULL OR ne.bookId = :bookId)
            AND (:search IS NULL
                 OR ne.textContent LIKE :search ESCAPE '\\'
                 OR ne.noteContent LIKE :search ESCAPE '\\'
                 OR ne.bookTitle LIKE :search ESCAPE '\\'
                 OR ne.chapterTitle LIKE :search ESCAPE '\\')
            """)
    Page<EntryProjection> findEntries(@Param("userId") Long userId,
                                      @Param("types") Set<String> types,
                                      @Param("bookId") Long bookId,
                                      @Param("search") String search,
                                      Pageable pageable);

    @Query("""
            SELECT DISTINCT ne.bookId as bookId, ne.bookTitle as bookTitle
            FROM NotebookEntryView ne
            WHERE ne.userId = :userId
            AND (:search IS NULL OR ne.bookTitle LIKE :search ESCAPE '\\')
            ORDER BY ne.bookTitle
            """)
    List<BookProjection> findBooksWithAnnotations(@Param("userId") Long userId,
                                                  @Param("search") String search,
                                                  Pageable pageable);

    @Query(value = """
            SELECT ne.bookId as bookId,
                   ne.bookTitle as bookTitle,
                   count(ne) as noteCount,
                   ne.coverUpdatedOn as coverUpdatedOn
            FROM NotebookEntryView ne
            WHERE ne.userId = :userId
            AND (:search IS NULL OR ne.bookTitle LIKE :search ESCAPE '\\')
            GROUP BY ne.bookId, ne.bookTitle, ne.coverUpdatedOn
            ORDER BY ne.bookTitle
            """,
           countQuery = """
            SELECT count(DISTINCT ne.bookId)
            FROM NotebookEntryView ne
            WHERE ne.userId = :userId
            AND (:search IS NULL OR ne.bookTitle LIKE :search ESCAPE '\\')
            """)
    Page<BookWithCountProjection> findBooksWithAnnotationsPaginated(@Param("userId") Long userId,
                                                                    @Param("search") String search,
                                                                    Pageable pageable);
}
