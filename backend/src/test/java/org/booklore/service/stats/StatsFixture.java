package org.booklore.service.stats;

import jakarta.persistence.EntityManager;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.stats.StatsQuery;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.ReadingSessionEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class StatsFixture {

    static final StatsQuery NO_FILTER = new StatsQuery(null, null, null, null);

    private final EntityManager em;
    final BookLoreUserEntity reader;
    final BookLoreUserEntity otherReader;
    final LibraryEntity mainLibrary;
    final LibraryEntity otherLibrary;

    StatsFixture(EntityManager em) {
        this.em = em;
        reader = user("reader");
        otherReader = user("other");
        mainLibrary = library("Main");
        otherLibrary = library("Other");
    }

    static StatsQuery zone(String zone) {
        return new StatsQuery(zone, null, null, null);
    }

    BookLoreUser member(LibraryEntity... libraries) {
        List<Library> assigned = new ArrayList<>();
        for (LibraryEntity library : libraries) {
            assigned.add(Library.builder().id(library.getId()).build());
        }
        return dtoUser(false, assigned);
    }

    BookLoreUser admin() {
        return dtoUser(true, List.of());
    }

    BookEntity book(String title, Consumer<BookMetadataEntity> metadata) {
        return book(mainLibrary, title, metadata);
    }

    BookEntity book(LibraryEntity library, String title, Consumer<BookMetadataEntity> metadata) {
        LibraryPathEntity path = LibraryPathEntity.builder().library(library).path("/" + library.getName()).build();
        em.persist(path);
        BookEntity book = BookEntity.builder().library(library).libraryPath(path).addedOn(Instant.now().minusSeconds(3600)).deleted(false).build();
        em.persist(book);
        BookMetadataEntity bookMetadata = BookMetadataEntity.builder().book(book).title(title).build();
        metadata.accept(bookMetadata);
        em.persist(bookMetadata);
        book.setMetadata(bookMetadata);
        return book;
    }

    BookFileEntity file(BookEntity book, BookFileType type, String name) {
        BookFileEntity file = BookFileEntity.builder().book(book).bookType(type).isBookFormat(true)
                .fileName(name).fileSubPath("").fileSizeKb(10L).build();
        em.persist(file);
        return file;
    }

    void progress(BookEntity book, Consumer<UserBookProgressEntity.UserBookProgressEntityBuilder> values) {
        progress(book, reader, values);
    }

    void progress(BookEntity book, BookLoreUserEntity user, Consumer<UserBookProgressEntity.UserBookProgressEntityBuilder> values) {
        UserBookProgressEntity.UserBookProgressEntityBuilder builder = UserBookProgressEntity.builder().book(book).user(user);
        values.accept(builder);
        em.persist(builder.build());
    }

    void session(BookEntity book, String start, String end) {
        session(book, reader, start, end);
    }

    void session(BookEntity book, BookLoreUserEntity user, String start, String end) {
        Instant startTime = Instant.parse(start);
        Instant endTime = Instant.parse(end);
        em.persist(ReadingSessionEntity.builder().book(book).user(user).bookType(BookFileType.EPUB)
                .startTime(startTime).endTime(endTime)
                .durationSeconds((int) Duration.between(startTime, endTime).toSeconds())
                .endProgress(50f).build());
    }

    void flush() {
        em.flush();
    }

    private BookLoreUser dtoUser(boolean isAdmin, List<Library> libraries) {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(isAdmin);
        return BookLoreUser.builder().id(reader.getId()).assignedLibraries(libraries).permissions(permissions).build();
    }

    private BookLoreUserEntity user(String username) {
        BookLoreUserEntity user = BookLoreUserEntity.builder().username(username).passwordHash("x").name(username).build();
        em.persist(user);
        return user;
    }

    private LibraryEntity library(String name) {
        LibraryEntity library = LibraryEntity.builder().name(name).icon("book").watch(false).build();
        em.persist(library);
        return library;
    }
}
