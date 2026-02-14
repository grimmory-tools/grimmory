package org.booklore.repository;

import org.booklore.model.entity.AnnotationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface NotebookEntryRepository extends Repository<AnnotationEntity, Long> {

    String ENTRIES_UNION =
            "SELECT a.id, 'HIGHLIGHT' AS type, a.user_id, a.book_id, bm.title AS book_title, " +
            "a.text, a.note, a.color, a.style, a.chapter_title, a.created_at, a.updated_at " +
            "FROM annotations a JOIN book_metadata bm ON bm.book_id = a.book_id " +
            "UNION ALL " +
            "SELECT n.id, 'NOTE' AS type, n.user_id, n.book_id, bm.title AS book_title, " +
            "n.selected_text, n.note_content, n.color, NULL, n.chapter_title, n.created_at, n.updated_at " +
            "FROM book_notes_v2 n JOIN book_metadata bm ON bm.book_id = n.book_id " +
            "UNION ALL " +
            "SELECT b.id, 'BOOKMARK' AS type, b.user_id, b.book_id, bm.title AS book_title, " +
            "b.title, b.notes, b.color, NULL, NULL, b.created_at, b.updated_at " +
            "FROM book_marks b JOIN book_metadata bm ON bm.book_id = b.book_id";

    String ENTRIES_FILTER =
            " WHERE t.user_id = :userId AND t.type IN (:types)" +
            " AND (:bookId IS NULL OR t.book_id = :bookId)" +
            " AND (:search IS NULL" +
            " OR t.text LIKE :search ESCAPE '\\\\'" +
            " OR t.note LIKE :search ESCAPE '\\\\'" +
            " OR t.book_title LIKE :search ESCAPE '\\\\'" +
            " OR t.chapter_title LIKE :search ESCAPE '\\\\')";

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
        LocalDateTime getCreatedAt();
        LocalDateTime getUpdatedAt();
    }

    interface BookProjection {
        Long getBookId();
        String getBookTitle();
    }

    @Query(value = "SELECT t.id, t.type, t.book_id AS bookId, t.book_title AS bookTitle, " +
                   "t.text, t.note, t.color, t.style, t.chapter_title AS chapterTitle, " +
                   "t.created_at AS createdAt, t.updated_at AS updatedAt " +
                   "FROM (" + ENTRIES_UNION + ") t" + ENTRIES_FILTER,
           countQuery = "SELECT COUNT(*) FROM (" + ENTRIES_UNION + ") t" + ENTRIES_FILTER,
           nativeQuery = true)
    Page<EntryProjection> findEntries(@Param("userId") Long userId,
                                      @Param("types") Set<String> types,
                                      @Param("bookId") Long bookId,
                                      @Param("search") String search,
                                      Pageable pageable);

    @Query(value = "SELECT DISTINCT t.book_id AS bookId, t.book_title AS bookTitle FROM (" +
                   "SELECT a.book_id, bm.title AS book_title FROM annotations a " +
                   "JOIN book_metadata bm ON bm.book_id = a.book_id WHERE a.user_id = :userId " +
                   "UNION " +
                   "SELECT n.book_id, bm.title AS book_title FROM book_notes_v2 n " +
                   "JOIN book_metadata bm ON bm.book_id = n.book_id WHERE n.user_id = :userId " +
                   "UNION " +
                   "SELECT b.book_id, bm.title AS book_title FROM book_marks b " +
                   "JOIN book_metadata bm ON bm.book_id = b.book_id WHERE b.user_id = :userId" +
                   ") t WHERE (:search IS NULL OR t.book_title LIKE :search ESCAPE '\\\\') " +
                   "ORDER BY t.book_title",
           nativeQuery = true)
    List<BookProjection> findBooksWithAnnotations(@Param("userId") Long userId,
                                                  @Param("search") String search,
                                                  Pageable pageable);
}
