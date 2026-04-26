package org.booklore.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.task.TaskCronService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests verifying Hibernate entity relationships, lazy loading,
 * and transaction boundaries work correctly after the Hibernate refactoring.
 *
 * These tests run against H2 with create-drop DDL, exercising real JPA persistence
 * to catch regressions that unit tests with mocks cannot detect.
 */
@SpringBootTest(classes = BookloreApplication.class)
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.path-config=build/tmp/test-config",
        "app.bookdrop-folder=build/tmp/test-bookdrop",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false",
        "app.task.scan-library-cron=*/1 * * * * *",
        "app.task.process-bookdrop-cron=*/1 * * * * *",
        "app.features.oidc-enabled=false",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false"
})
@Import(HibernateRegressionTest.TestConfig.class)
class HibernateRegressionTest {

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserBookProgressRepository userBookProgressRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.boot.test.context.TestConfiguration
    public static class TestConfig {
        @Bean("flyway")
        @Primary
        public Flyway flyway() {
            return mock(Flyway.class);
        }

        @Bean
        @Primary
        public TaskCronService taskCronService() {
            return mock(TaskCronService.class);
        }
    }

    @Test
    void contextLoads() {
        assertThat(libraryRepository).isNotNull();
        assertThat(bookRepository).isNotNull();
    }

    @Nested
    class KoboSyncProgressGraph {

        @Test
        void findAllBooksNeedingKoboSync_loadsBookFilesForDetachedUse() {
            KoboProgressFixture fixture = createKoboProgressFixture();
            entityManager.flush();
            entityManager.clear();

            List<UserBookProgressEntity> result = userBookProgressRepository.findAllBooksNeedingKoboSync(
                    fixture.userId(),
                    fixture.snapshotId()
            );

            assertThat(result).hasSize(1);

            UserBookProgressEntity progress = result.getFirst();
            entityManager.clear();

            BookFileEntity primaryFile = progress.getBook().getPrimaryBookFile();
            assertThat(primaryFile).isNotNull();
            assertThat(primaryFile.getBookType()).isEqualTo(BookFileType.EPUB);
        }

        @Test
        void findByUserIdAndBookIdForKoboSync_loadsBookFilesForDetachedUse() {
            KoboProgressFixture fixture = createKoboProgressFixture();
            entityManager.flush();
            entityManager.clear();

            UserBookProgressEntity progress = userBookProgressRepository
                    .findByUserIdAndBookIdForKoboSync(fixture.userId(), fixture.bookId())
                    .orElseThrow();
            entityManager.clear();

            BookFileEntity primaryFile = progress.getBook().getPrimaryBookFile();
            assertThat(primaryFile).isNotNull();
            assertThat(primaryFile.getCurrentHash()).isEqualTo("kobo-hash-" + fixture.bookId());
        }

        @Test
        void findByIdWithBookFiles_loadsBookFilesForDetachedUse() {
            KoboProgressFixture fixture = createKoboProgressFixture();
            entityManager.flush();
            entityManager.clear();

            BookEntity book = bookRepository.findByIdWithBookFiles(fixture.bookId()).orElseThrow();
            entityManager.clear();

            BookFileEntity primaryFile = book.getPrimaryBookFile();
            assertThat(primaryFile).isNotNull();
            assertThat(primaryFile.getCurrentHash()).isEqualTo("kobo-hash-" + fixture.bookId());
        }

        private KoboProgressFixture createKoboProgressFixture() {
            String suffix = UUID.randomUUID().toString();

            LibraryEntity library = LibraryEntity.builder()
                    .name("Kobo Graph Library " + suffix)
                    .icon("book")
                    .watch(false)
                    .formatPriority(new ArrayList<>(List.of(BookFileType.EPUB)))
                    .build();
            entityManager.persist(library);

            LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                    .library(library)
                    .path("/kobo/books/" + suffix)
                    .build();
            entityManager.persist(libraryPath);

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            entityManager.persist(book);
            entityManager.flush();

            BookFileEntity bookFile = BookFileEntity.builder()
                    .book(book)
                    .fileName("book.epub")
                    .fileSubPath(".")
                    .isBookFormat(true)
                    .bookType(BookFileType.EPUB)
                    .fileSizeKb(256L)
                    .currentHash("kobo-hash-" + book.getId())
                    .build();
            book.getBookFiles().add(bookFile);
            entityManager.persist(bookFile);

            BookLoreUserEntity user = BookLoreUserEntity.builder()
                    .username("kobo-user-" + suffix)
                    .passwordHash("password")
                    .isDefaultPassword(false)
                    .name("Kobo User")
                    .build();
            entityManager.persist(user);

            UserBookProgressEntity progress = UserBookProgressEntity.builder()
                    .user(user)
                    .book(book)
                    .epubProgress("epubcfi(/6/2!/4/2/2:1)")
                    .epubProgressPercent(42f)
                    .lastReadTime(Instant.now())
                    .build();
            entityManager.persist(progress);

            KoboLibrarySnapshotEntity snapshot = KoboLibrarySnapshotEntity.builder()
                    .id("snapshot-" + suffix)
                    .userId(user.getId())
                    .createdDate(LocalDateTime.now())
                    .build();
            entityManager.persist(snapshot);

            KoboSnapshotBookEntity snapshotBook = KoboSnapshotBookEntity.builder()
                    .snapshot(snapshot)
                    .bookId(book.getId())
                    .fileHash(bookFile.getCurrentHash())
                    .synced(false)
                    .build();
            entityManager.persist(snapshotBook);

            return new KoboProgressFixture(user.getId(), book.getId(), snapshot.getId());
        }

        private record KoboProgressFixture(Long userId, Long bookId, String snapshotId) {
        }
    }

    @Nested
    class LibraryPathBackReference {

        /**
         * Regression: createLibrary() must set LibraryPathEntity.library back-reference.
         * Without enableAssociationManagement, Hibernate no longer auto-manages
         * bidirectional relationships. If the back-reference is missing, the FK
         * column library_id is NULL and paths are not linked to the library.
         */
        @Test
        void libraryPathsMustHaveLibraryBackReference_whenPersistedViaCascade() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("Cascade Test Library")
                    .icon("book")
                    .watch(false)
                    .libraryPaths(new ArrayList<>(List.of(
                            LibraryPathEntity.builder().path("/books/fiction").build(),
                            LibraryPathEntity.builder().path("/books/nonfiction").build()
                    )))
                    .build();

            // Simulate what createLibrary() must do: set the back-reference
            library.getLibraryPaths().forEach(p -> p.setLibrary(library));

            LibraryEntity saved = libraryRepository.saveAndFlush(library);
            entityManager.clear();

            LibraryEntity reloaded = libraryRepository.findByIdWithPaths(saved.getId()).orElseThrow();

            assertThat(reloaded.getLibraryPaths()).hasSize(2);
            for (LibraryPathEntity path : reloaded.getLibraryPaths()) {
                assertThat(path.getLibrary()).isNotNull();
                assertThat(path.getLibrary().getId()).isEqualTo(reloaded.getId());
            }
        }

        /**
         * Verify that the owning side FK is actually persisted to the DB and
         * the library can find its paths after a fresh session load.
         */
        @Test
        void libraryPathForeignKey_isPersistedCorrectly() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("FK Test Library")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);
            entityManager.flush();

            LibraryPathEntity path = LibraryPathEntity.builder()
                    .library(library)
                    .path("/test/path")
                    .build();
            entityManager.persist(path);
            entityManager.flush();
            entityManager.clear();

            // Verify via JPQL that the FK is set
            Long count = entityManager.createQuery(
                    "SELECT COUNT(lp) FROM LibraryPathEntity lp WHERE lp.library.id = :libId", Long.class)
                    .setParameter("libId", library.getId())
                    .getSingleResult();

            assertThat(count).isEqualTo(1L);
        }

        /**
         * Without back-reference, cascade persist puts NULL in library_id.
         * This test verifies the issue exists if back-ref is not set,
         * by checking constraint violation.
         */
        @Test
        void cascadePersist_withoutBackReference_failsWithConstraintViolation() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("Missing BackRef Library")
                    .icon("book")
                    .watch(false)
                    .libraryPaths(new ArrayList<>(List.of(
                            LibraryPathEntity.builder().path("/dangling").build()
                    )))
                    .build();
            // Intentionally NOT setting library back-reference

            Assertions.assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> {
                        libraryRepository.saveAndFlush(library);
                        entityManager.flush();
                    },
                    "Should fail because library_id is NOT NULL but no back-reference was set"
            );
        }
    }

    @Nested
    class EntityGraphQueries {

        private LibraryEntity library;
        private LibraryPathEntity libraryPath;

        @BeforeEach
        void setUp() {
            library = LibraryEntity.builder()
                    .name("EntityGraph Test Library")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            libraryPath = LibraryPathEntity.builder()
                    .library(library)
                    .path("/test/books")
                    .build();
            entityManager.persist(libraryPath);
            entityManager.flush();
        }

        /**
         * findByIdWithPaths must eagerly load libraryPaths so they are
         * accessible outside a Hibernate session (e.g., in virtual threads).
         */
        @Test
        void findByIdWithPaths_loadsLibraryPathsEagerly() {
            entityManager.clear();

            LibraryEntity loaded = libraryRepository.findByIdWithPaths(library.getId()).orElseThrow();

            // Detach to simulate outside-session access
            entityManager.detach(loaded);

            // This must NOT throw LazyInitializationException
            assertThat(loaded.getLibraryPaths()).hasSize(1);
            assertThat(loaded.getLibraryPaths().get(0).getPath()).isEqualTo("/test/books");
        }

        /**
         * findByIdWithBooks must eagerly load bookEntities.
         */
        @Test
        void findByIdWithBooks_loadsBookEntitiesEagerly() {
            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            LibraryEntity loaded = libraryRepository.findByIdWithBooks(library.getId()).orElseThrow();
            entityManager.detach(loaded);

            assertThat(loaded.getBookEntities()).hasSize(1);
        }

        /**
         * findByIdWithBookFiles must eagerly load bookFiles via EntityGraph.
         */
        @Test
        void findByIdWithBookFiles_loadsBookFilesEagerly() {
            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            entityManager.persist(book);
            entityManager.flush();

            BookFileEntity bookFile = BookFileEntity.builder()
                    .book(book)
                    .fileName("test.epub")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.EPUB)
                    .fileSizeKb(100L)
                    .initialHash("abc123")
                    .currentHash("abc123")
                    .addedOn(Instant.now())
                    .build();
            entityManager.persist(bookFile);
            entityManager.flush();
            entityManager.clear();

            BookEntity loaded = bookRepository.findByIdWithBookFiles(book.getId()).orElseThrow();
            entityManager.detach(loaded);

            // Must NOT throw LazyInitializationException
            assertThat(loaded.getBookFiles()).hasSize(1);
            assertThat(loaded.getBookFiles().get(0).getFileName()).isEqualTo("test.epub");
        }
    }

    @Nested
    class BookEntityDefaults {

        /**
         * Regression: BookEntity.deleted must default to FALSE (not null)
         * so that queries filtering on deleted work correctly.
         */
        @Test
        void bookEntity_deletedFieldDefaultsToFalse() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("Defaults Test")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            LibraryPathEntity path = LibraryPathEntity.builder()
                    .library(library)
                    .path("/test")
                    .build();
            entityManager.persist(path);

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(path)
                    .addedOn(Instant.now())
                    .build();
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            BookEntity loaded = entityManager.find(BookEntity.class, book.getId());
            assertThat(loaded.getDeleted()).isEqualTo(Boolean.FALSE);
        }

        /**
         * findByIdWithBookFiles must find books where deleted = false.
         */
        @Test
        void findByIdWithBookFiles_findsNonDeletedBook() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("Non-deleted Test")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            LibraryPathEntity path = LibraryPathEntity.builder()
                    .library(library)
                    .path("/test")
                    .build();
            entityManager.persist(path);

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(path)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            assertThat(bookRepository.findByIdWithBookFiles(book.getId())).isPresent();
        }

        /**
         * findByIdWithBookFiles must NOT find soft-deleted books.
         */
        @Test
        void findByIdWithBookFiles_excludesDeletedBooks() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("Deleted Test")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            LibraryPathEntity path = LibraryPathEntity.builder()
                    .library(library)
                    .path("/test")
                    .build();
            entityManager.persist(path);

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(path)
                    .addedOn(Instant.now())
                    .deleted(true)
                    .build();
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            assertThat(bookRepository.findByIdWithBookFiles(book.getId())).isEmpty();
        }
    }

    @Nested
    class CollectionInitialization {

        /**
         * Verify that @Builder.Default collection fields are initialized
         * and not null after entity construction via builder.
         */
        @Test
        void libraryEntity_collectionsAreInitialized() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("Init Test")
                    .icon("book")
                    .watch(false)
                    .build();

            assertThat(library.getLibraryPaths()).isNotNull();
            assertThat(library.getBookEntities()).isNotNull();
            assertThat(library.getUsers()).isNotNull();
            assertThat(library.getFormatPriority()).isNotNull();
        }

        @Test
        void bookEntity_collectionsAreInitialized() {
            BookEntity book = BookEntity.builder()
                    .addedOn(Instant.now())
                    .build();

            assertThat(book.getBookFiles()).isNotNull();
            assertThat(book.getShelves()).isNotNull();
            assertThat(book.getUserBookProgress()).isNotNull();
            assertThat(book.getDeleted()).isEqualTo(Boolean.FALSE);
            assertThat(book.getIsPhysical()).isEqualTo(Boolean.FALSE);
        }
    }

    @Nested
    class LibraryScanSimulation {

        /**
         * Simulate the library scan flow: create library with paths,
         * then verify paths can be loaded and iterated for scanning.
         * This is the exact flow broken when enableAssociationManagement
         * was removed paths had NULL library_id so scanning found 0 paths.
         */
        @Test
        void libraryWithPaths_canBeLoadedAndIteratedForScanning() {
            // Simulate LibraryService.createLibrary() flow
            LibraryEntity library = LibraryEntity.builder()
                    .name("Scan Simulation Library")
                    .icon("book")
                    .watch(false)
                    .libraryPaths(new ArrayList<>(List.of(
                            LibraryPathEntity.builder().path("/books").build(),
                            LibraryPathEntity.builder().path("/audiobooks").build()
                    )))
                    .build();

            library.getLibraryPaths().forEach(p -> p.setLibrary(library));

            libraryRepository.saveAndFlush(library);
            entityManager.clear();

            LibraryEntity loaded = libraryRepository.findByIdWithPaths(library.getId()).orElseThrow();

            List<String> pathStrings = loaded.getLibraryPaths().stream()
                    .map(LibraryPathEntity::getPath)
                    .collect(Collectors.toList());

            assertThat(pathStrings).containsExactlyInAnyOrder("/books", "/audiobooks");
        }

        /**
         * Verify the updateLibrary flow — updating paths preserves back-references.
         */
        @Test
        void updateLibraryPaths_preservesBackReferences() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("Update Test Library")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            LibraryPathEntity path1 = LibraryPathEntity.builder()
                    .library(library)
                    .path("/old/path")
                    .build();
            entityManager.persist(path1);
            entityManager.flush();
            entityManager.clear();

            // Simulate updateLibrary() — load, clear paths, add new ones
            LibraryEntity loaded = libraryRepository.findByIdWithPaths(library.getId()).orElseThrow();
            loaded.getLibraryPaths().clear();

            LibraryPathEntity newPath = LibraryPathEntity.builder()
                    .library(loaded) // back-reference set here
                    .path("/new/path")
                    .build();
            loaded.getLibraryPaths().add(newPath);

            libraryRepository.saveAndFlush(loaded);
            entityManager.clear();

            LibraryEntity reloaded = libraryRepository.findByIdWithPaths(library.getId()).orElseThrow();
            assertThat(reloaded.getLibraryPaths()).hasSize(1);
            assertThat(reloaded.getLibraryPaths().get(0).getPath()).isEqualTo("/new/path");
            assertThat(reloaded.getLibraryPaths().get(0).getLibrary().getId()).isEqualTo(library.getId());
        }
    }

    @Nested
    class BookCreationFlow {

        /**
         * Simulate BookCreatorService.createShellBook() — verify book with
         * related entities can be saved and reloaded with all relationships intact.
         */
        @Test
        void createShellBook_savesAllRelationships() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("Book Creation Library")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                    .library(library)
                    .path("/books")
                    .build();
            entityManager.persist(libraryPath);
            entityManager.flush();

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .bookFiles(new ArrayList<>())
                    .build();

            BookFileEntity bookFile = BookFileEntity.builder()
                    .book(book)
                    .fileName("test.epub")
                    .fileSubPath("")
                    .isBookFormat(true)
                    .bookType(BookFileType.EPUB)
                    .fileSizeKb(500L)
                    .initialHash("hash1")
                    .currentHash("hash1")
                    .addedOn(Instant.now())
                    .build();
            book.getBookFiles().add(bookFile);

            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Test Book Title")
                    .build();
            book.setMetadata(metadata);

            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            BookEntity loaded = bookRepository.findByIdWithBookFiles(book.getId()).orElseThrow();

            assertThat(loaded.getLibrary().getId()).isEqualTo(library.getId());
            assertThat(loaded.getLibraryPath().getId()).isEqualTo(libraryPath.getId());
            assertThat(loaded.getBookFiles()).hasSize(1);
            assertThat(loaded.getBookFiles().get(0).getFileName()).isEqualTo("test.epub");
            assertThat(loaded.getMetadata().getTitle()).isEqualTo("Test Book Title");
            assertThat(loaded.getDeleted()).isFalse();
        }

        /**
         * Verify that saveAndFlush generates an ID that can be used
         * to refetch the book — critical for bookdrop finalization flow
         * where processFile() saves in REQUIRES_NEW and the parent
         * transaction needs to find it.
         */
        @Test
        void saveAndFlush_generatesIdForImmediateRetrieval() {
            LibraryEntity library = LibraryEntity.builder()
                    .name("SaveFlush Library")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                    .library(library)
                    .path("/books")
                    .build();
            entityManager.persist(libraryPath);
            entityManager.flush();

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .build();

            BookEntity saved = bookRepository.saveAndFlush(book);

            assertThat(saved.getId()).isNotNull();
            assertThat(bookRepository.findByIdWithBookFiles(saved.getId())).isPresent();
        }
    }

    @Nested
    class FindAllWithPathsQuery {

        /**
         * Verify findAllWithPaths EntityGraph loads paths for all libraries.
         */
        @Test
        void findAllWithPaths_loadsPathsForAllLibraries() {
            LibraryEntity lib1 = LibraryEntity.builder().name("Lib1").icon("a").watch(false).build();
            entityManager.persist(lib1);
            LibraryPathEntity path1 = LibraryPathEntity.builder().library(lib1).path("/lib1").build();
            entityManager.persist(path1);

            LibraryEntity lib2 = LibraryEntity.builder().name("Lib2").icon("b").watch(false).build();
            entityManager.persist(lib2);
            LibraryPathEntity path2 = LibraryPathEntity.builder().library(lib2).path("/lib2").build();
            entityManager.persist(path2);
            entityManager.flush();
            entityManager.clear();

            List<LibraryEntity> libraries = libraryRepository.findAllWithPaths();

            assertThat(libraries).hasSizeGreaterThanOrEqualTo(2);
            for (LibraryEntity lib : libraries) {
                entityManager.detach(lib);
                assertThat(lib.getLibraryPaths()).isNotEmpty();
            }
        }
    }

    @Nested
    class MetadataDescriptionAccess {

        private LibraryEntity library;
        private LibraryPathEntity libraryPath;

        @BeforeEach
        void setUp() {
            library = LibraryEntity.builder()
                    .name("Description Test Library")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            libraryPath = LibraryPathEntity.builder()
                    .library(library)
                    .path("/test/books")
                    .build();
            entityManager.persist(libraryPath);
            entityManager.flush();
        }

        /**
         * Regression: BookMetadataEntity.description must be accessible after detaching.
         * Previously, description was @Basic(fetch=LAZY) which caused
         * LazyInitializationException in MetadataChangeDetector and OPDS feeds.
         */
        @Test
        void descriptionIsAccessibleOnDetachedEntity() {
            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Description Test Book")
                    .description("A long description of this book for testing purposes.")
                    .build();
            book.setMetadata(metadata);
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            BookEntity loaded = bookRepository.findAllWithMetadataByIds(
                    Collections.singleton(book.getId())).stream().findFirst().orElseThrow();
            entityManager.detach(loaded);

            assertThat(loaded.getMetadata().getDescription())
                    .isEqualTo("A long description of this book for testing purposes.");
        }

        /**
         * Regression: findAllWithMetadataByIds EntityGraph must load description
         * so that MetadataChangeDetector.isDifferent() can compare it.
         */
        @Test
        void findAllWithMetadataByIds_loadsDescriptionEagerly() {
            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Metadata Load Test")
                    .description("Detailed plot summary here.")
                    .build();
            book.setMetadata(metadata);
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            List<BookEntity> books = bookRepository.findAllWithMetadataByIds(
                    Collections.singleton(book.getId()));

            assertThat(books).hasSize(1);

            entityManager.detach(books.get(0));

            assertThat(books.get(0).getMetadata().getDescription())
                    .isEqualTo("Detailed plot summary here.");
            assertThat(books.get(0).getMetadata().getTitle())
                    .isEqualTo("Metadata Load Test");
        }

        /**
         * Verify embeddingVector and searchText remain lazy and are NOT loaded
         * by default EntityGraphs, but ARE accessible within a session.
         */
        @Test
        void lazyFieldsAccessibleWithinSession() {
            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Lazy Fields Test")
                    .embeddingVector("[0.1, 0.2, 0.3]")
                    .build();
            book.setMetadata(metadata);
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            BookEntity loaded = bookRepository.findByIdWithMetadata(book.getId()).orElseThrow();
            assertThat(loaded.getMetadata().getEmbeddingVector()).isEqualTo("[0.1, 0.2, 0.3]");
            assertThat(loaded.getMetadata().getSearchText()).isNotNull();
        }
    }

    @Nested
    class CategoryMappingIntegrity {

        private LibraryEntity library;
        private LibraryPathEntity libraryPath;

        @BeforeEach
        void setUp() {
            library = LibraryEntity.builder()
                    .name("Category Test Library")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            libraryPath = LibraryPathEntity.builder()
                    .library(library)
                    .path("/test/books")
                    .build();
            entityManager.persist(libraryPath);
            entityManager.flush();
        }

        /**
         * Regression: Saving a book with categories must not produce duplicate
         * entries in book_metadata_category_mapping. The old double-save pattern
         * (service saves, then controller saves again) caused duplicate key errors.
         */
        @Test
        void categoryMappings_noDuplicatesAfterSave() {
            CategoryEntity cat1 = CategoryEntity.builder().name("Fiction").build();
            CategoryEntity cat2 = CategoryEntity.builder().name("Science").build();
            entityManager.persist(cat1);
            entityManager.persist(cat2);

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Category Test Book")
                    .categories(new HashSet<>(Set.of(cat1, cat2)))
                    .build();
            book.setMetadata(metadata);
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            // Verify categories persisted correctly
            BookEntity loaded = bookRepository.findAllWithMetadataByIds(
                    Collections.singleton(book.getId())).stream().findFirst().orElseThrow();

            assertThat(loaded.getMetadata().getCategories()).hasSize(2);
            assertThat(loaded.getMetadata().getCategories().stream()
                    .map(CategoryEntity::getName)
                    .collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder("Fiction", "Science");

            Long mappingCount = entityManager.createQuery(
                    "SELECT COUNT(1) FROM BookMetadataEntity m JOIN m.categories c WHERE m.book.id = :bookId", Long.class)
                    .setParameter("bookId", book.getId())
                    .getSingleResult();
            assertThat(mappingCount).isEqualTo(2L);
        }

        /**
         * Updating categories on a managed entity should replace correctly
         * without duplicate key violations.
         */
        @Test
        void updateCategories_replacesWithoutDuplicates() {
            CategoryEntity cat1 = CategoryEntity.builder().name("OldCat1").build();
            CategoryEntity cat2 = CategoryEntity.builder().name("OldCat2").build();
            CategoryEntity cat3 = CategoryEntity.builder().name("NewCat3").build();
            entityManager.persist(cat1);
            entityManager.persist(cat2);
            entityManager.persist(cat3);

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Update Category Test")
                    .categories(new HashSet<>(Set.of(cat1, cat2)))
                    .build();
            book.setMetadata(metadata);
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            BookEntity loaded = bookRepository.findAllWithMetadataByIds(
                    Collections.singleton(book.getId())).stream().findFirst().orElseThrow();

            CategoryEntity managedCat2 = entityManager.find(CategoryEntity.class, cat2.getId());
            CategoryEntity managedCat3 = entityManager.find(CategoryEntity.class, cat3.getId());

            loaded.getMetadata().getCategories().clear();
            loaded.getMetadata().getCategories().add(managedCat2);
            loaded.getMetadata().getCategories().add(managedCat3);

            entityManager.flush();
            entityManager.clear();

            BookEntity reloaded = bookRepository.findAllWithMetadataByIds(
                    Collections.singleton(book.getId())).stream().findFirst().orElseThrow();

            assertThat(reloaded.getMetadata().getCategories()).hasSize(2);
            assertThat(reloaded.getMetadata().getCategories().stream()
                    .map(CategoryEntity::getName)
                    .collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder("OldCat2", "NewCat3");
        }
    }

    @Nested
    class EmbeddingBatchProcessing {

        private LibraryEntity library;
        private LibraryPathEntity libraryPath;

        @BeforeEach
        void setUp() {
            library = LibraryEntity.builder()
                    .name("Embedding Test Library")
                    .icon("book")
                    .watch(false)
                    .build();
            entityManager.persist(library);

            libraryPath = LibraryPathEntity.builder()
                    .library(library)
                    .path("/test/books")
                    .build();
            entityManager.persist(libraryPath);
            entityManager.flush();
        }

        /**
         * Regression: findAllFullBooksBatch must load metadata.authors and
         * metadata.categories eagerly (via Hibernate.initialize) so they are
         * accessible on detached entities for embedding computation.
         */
        @Test
        void findAllFullBooksBatch_initializesLazyCollections() {
            CategoryEntity cat = CategoryEntity.builder().name("TestCat").build();
            entityManager.persist(cat);

            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Batch Test Book")
                    .categories(new HashSet<>(Set.of(cat)))
                    .build();
            book.setMetadata(metadata);
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            List<BookEntity> batch = bookRepository.findAllFullBooksBatch(
                    PageRequest.of(0, 100));

            assertThat(batch).isNotEmpty();

            BookEntity loaded = batch.stream()
                    .filter(b -> b.getId().equals(book.getId()))
                    .findFirst()
                    .orElseThrow();

            assertThat(loaded.getMetadata().getCategories()).hasSize(1);
            assertThat(loaded.getMetadata().getTitle()).isEqualTo("Batch Test Book");
        }

        /**
         * Verify embedding vector can be compared and updated within a session.
         */
        @Test
        void embeddingVector_comparableAndUpdatableInSession() {
            BookEntity book = BookEntity.builder()
                    .library(library)
                    .libraryPath(libraryPath)
                    .addedOn(Instant.now())
                    .deleted(false)
                    .build();
            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .book(book)
                    .title("Embedding Update Test")
                    .embeddingVector("[0.1, 0.2]")
                    .build();
            book.setMetadata(metadata);
            entityManager.persist(book);
            entityManager.flush();
            entityManager.clear();

            BookEntity loaded = bookRepository.findAllWithMetadataByIds(
                    Collections.singleton(book.getId())).stream().findFirst().orElseThrow();

            String oldEmbedding = loaded.getMetadata().getEmbeddingVector();
            assertThat(oldEmbedding).isEqualTo("[0.1, 0.2]");

            loaded.getMetadata().setEmbeddingVector("[0.3, 0.4]");
            loaded.getMetadata().setEmbeddingUpdatedAt(Instant.now());
            entityManager.flush();
            entityManager.clear();

            BookEntity reloaded = bookRepository.findByIdWithMetadata(book.getId()).orElseThrow();
            assertThat(reloaded.getMetadata().getEmbeddingVector()).isEqualTo("[0.3, 0.4]");
        }
    }
}
