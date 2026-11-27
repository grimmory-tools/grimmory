package org.booklore.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.BookloreApplication;
import org.booklore.controller.MetadataController;
import org.booklore.exception.APIException;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataFetchTaskStatus;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.service.task.TaskCronService;
import org.booklore.service.ArchiveService;
import org.booklore.service.metadata.BookMetadataUpdater;
import org.booklore.service.metadata.bulk.BulkMetadataDatabaseService;
import org.booklore.service.metadata.bulk.BulkMetadataEpubInstaller;
import org.booklore.service.metadata.bulk.BulkMetadataJournal;
import org.booklore.service.metadata.bulk.BulkMetadataPreparationService;
import org.booklore.service.metadata.bulk.BulkMetadataRecoveryService;
import org.booklore.service.metadata.bulk.BulkMetadataRecoveryInspector;
import org.booklore.service.metadata.writer.EpubMetadataWriter;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import org.springframework.boot.test.context.TestConfiguration;

@SpringBootTest(classes = {
        BookloreApplication.class
})
@Transactional
@TestPropertySource(properties = {
        "logging.level.root=debug",
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
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false"
})
@Import(BookRepositoryDataJpaTest.TestConfig.class)
class BookRepositoryDataJpaTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookOpdsRepository bookOpdsRepository;

    @Autowired
    private BookFileRepository bookFileRepository;

    @Autowired
    private MetadataFetchJobRepository metadataFetchJobRepository;

    @Autowired
    private MetadataController metadataController;

    @Autowired
    private BulkMetadataJournal bulkMetadataJournal;

    @Autowired
    private BulkMetadataDatabaseService bulkMetadataDatabaseService;

    @Autowired
    private BulkMetadataEpubInstaller bulkMetadataEpubInstaller;

    @Autowired
    private BulkMetadataRecoveryService bulkMetadataRecoveryService;

    @Autowired
    private BulkMetadataJournalRepository bulkMetadataJournalRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @TestConfiguration
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
        assertThat(bookRepository).isNotNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalCommitsWithBookChangesAndRollsBackTogether() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS synthetic_bulk_change (id BIGINT PRIMARY KEY, title VARCHAR(50))");
        long bookId = 90000001L;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> jdbc.update(
                "INSERT INTO synthetic_bulk_change (id, title) VALUES (?, ?)", bookId, "Original"));
        String operationId = bulkMetadataJournal.enqueue("journal-rollback", bookId);
        assertThatThrownBy(() -> bulkMetadataJournal.recordDatabaseCommit(operationId))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE synthetic_bulk_change SET title = ? WHERE id = ?", "Changed", bookId);
            bulkMetadataJournal.recordDatabaseCommit(operationId);
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");

        assertThat(jdbc.queryForObject("SELECT title FROM synthetic_bulk_change WHERE id = ?", String.class, bookId))
                .isEqualTo("Original");
        assertThat(bulkMetadataJournalRepository.findById(operationId).orElseThrow().getStage())
                .isEqualTo(BulkMetadataJournalEntity.Stage.PENDING_DB);

        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE synthetic_bulk_change SET title = ? WHERE id = ?", "Changed", bookId);
            bulkMetadataJournal.recordDatabaseCommit(operationId);
        });
        assertThat(jdbc.queryForObject("SELECT title FROM synthetic_bulk_change WHERE id = ?", String.class, bookId))
                .isEqualTo("Changed");
        assertThat(bulkMetadataJournalRepository.findById(operationId).orElseThrow().getStage())
                .isEqualTo(BulkMetadataJournalEntity.Stage.DB_COMMITTED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalDatabaseUpdateIsAtomicAndChecksReservedBook() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long bookId = tx.execute(status -> {
            LibraryEntity library = LibraryEntity.builder().name("Bulk transaction").icon("book").watch(false).build();
            entityManager.persist(library);
            BookEntity book = BookEntity.builder().library(library).addedOn(Instant.now()).deleted(false).build();
            BookMetadataEntity metadata = BookMetadataEntity.builder().book(book).title("Original").build();
            book.setMetadata(metadata);
            entityManager.persist(book);
            entityManager.flush();
            return book.getId();
        });
        String operationId = bulkMetadataJournal.enqueue("database-update", bookId);
        BookMetadataUpdater updater = mock(BookMetadataUpdater.class);
        doAnswer(invocation -> {
            MetadataUpdateContext context = invocation.getArgument(0);
            assertThat(context.isDatabaseOnly()).isTrue();
            assertThat(context.isUpdateThumbnail()).isFalse();
            context.getBookEntity().getMetadata().setTitle(context.getMetadataUpdateWrapper().getMetadata().getTitle());
            return null;
        }).when(updater).setBookMetadata(any());
        var service = new BulkMetadataDatabaseService(bulkMetadataJournal, bookRepository, updater);
        BookMetadata fetched = BookMetadata.builder().title("Changed").build();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.apply(
                operationId, bookId + 1, fetched, MetadataReplaceMode.REPLACE_WHEN_PROVIDED, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.apply(operationId, bookId, fetched, MetadataReplaceMode.REPLACE_WHEN_PROVIDED, false);
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(jdbc.queryForObject("SELECT title FROM book_metadata WHERE book_id = ?", String.class, bookId))
                .isEqualTo("Original");
        assertThat(bulkMetadataJournalRepository.findById(operationId).orElseThrow().getStage())
                .isEqualTo(BulkMetadataJournalEntity.Stage.PENDING_DB);

        tx.executeWithoutResult(status -> service.apply(
                operationId, bookId, fetched, MetadataReplaceMode.REPLACE_WHEN_PROVIDED, false));
        assertThat(jdbc.queryForObject("SELECT title FROM book_metadata WHERE book_id = ?", String.class, bookId))
                .isEqualTo("Changed");
        assertThat(bulkMetadataJournalRepository.findById(operationId).orElseThrow().getStage())
                .isEqualTo(BulkMetadataJournalEntity.Stage.DB_COMMITTED);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.apply(
                operationId, bookId, fetched, MetadataReplaceMode.REPLACE_WHEN_PROVIDED, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalMigrationCreatesIdempotentSchemaWithBookReservation() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:bulk-journal-migration;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        var migration = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V153__Create_bulk_metadata_journal.sql"));
        migration.execute(source);
        migration.execute(source);
        var isolatedJdbc = new JdbcTemplate(source);
        String insert = """
                INSERT INTO bulk_metadata_journal
                (operation_id, job_id, book_id, active_book_id, stage, created_at, updated_at, version)
                VALUES (?, 'test', ?, ?, 'PENDING_DB', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """;
        isolatedJdbc.update(insert, "operation-1", 1L, 1L);
        assertThatThrownBy(() -> isolatedJdbc.update(insert, "operation-2", 1L, 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
        isolatedJdbc.update(insert, "operation-3", 1L, null);
        isolatedJdbc.update(insert, "operation-4", 1L, null);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalReservesBookAndPreservesUncertainFileState() {
        long bookId = 90000002L;
        String operationId = bulkMetadataJournal.enqueue("journal-stages", bookId);
        assertThatThrownBy(() -> bulkMetadataJournal.enqueue("other-job", bookId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> bulkMetadataJournal.markEpubApplying(operationId))
                .isInstanceOf(IllegalStateException.class);

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> bulkMetadataJournal.recordDatabaseCommit(operationId));
        Path root = Path.of("journal-test").toAbsolutePath();
        String oldHash = "a".repeat(64);
        String newHash = "b".repeat(64);
        bulkMetadataJournal.recordEpubPreparationIntent(operationId, new BulkMetadataJournal.EpubPreparationIntent(
                root.resolve("source.epub"), root.resolve("candidate.epub"), root.resolve("backup.epub"),
                oldHash));
        BulkMetadataJournalEntity preparing = bulkMetadataJournalRepository.findById(operationId).orElseThrow();
        assertThat(preparing.getStage()).isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_PREPARING);
        assertThat(preparing.getCandidatePath()).isEqualTo(root.resolve("candidate.epub").toString());
        assertThat(preparing.getCandidateSha256()).isNull();
        bulkMetadataJournal.recordEpubPrepared(operationId, newHash);
        bulkMetadataJournal.markEpubApplying(operationId);
        assertThatThrownBy(() -> bulkMetadataJournal.recordEpubApplied(operationId, oldHash))
                .isInstanceOf(IllegalArgumentException.class);
        bulkMetadataJournal.requireAttention(operationId, "INSTALL_UNCERTAIN");

        BulkMetadataJournalEntity entry = bulkMetadataJournalRepository.findById(operationId).orElseThrow();
        assertThat(entry.getStage()).isEqualTo(BulkMetadataJournalEntity.Stage.NEEDS_ATTENTION);
        assertThat(entry.getPreviousStage()).isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_APPLYING);
        assertThat(entry.getActiveBookId()).isEqualTo(bookId);
        assertThat(entry.getOriginalSha256()).isEqualTo(oldHash);
        assertThat(entry.getCandidateSha256()).isEqualTo(newHash);
        assertThat(bulkMetadataJournalRepository.findByJobIdOrderByOperationId("journal-stages", PageRequest.of(0, 10)))
                .hasSize(1);
        assertThatThrownBy(() -> bulkMetadataJournal.recordSafeFailure(operationId, "INSTALL_FAILED"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalSafeFailureReleasesOnlyUnmodifiedBook() {
        long bookId = 90000003L;
        String first = bulkMetadataJournal.enqueue("failure-job", bookId);
        bulkMetadataJournal.recordSafeFailure(first, "FETCH_FAILED");
        BulkMetadataJournalEntity failed = bulkMetadataJournalRepository.findById(first).orElseThrow();
        assertThat(failed.getStage()).isEqualTo(BulkMetadataJournalEntity.Stage.FAILED_SAFE);
        assertThat(failed.getActiveBookId()).isNull();
        assertThat(failed.getErrorCode()).isEqualTo("FETCH_FAILED");
        assertThat(bulkMetadataJournal.enqueue("retry-job", bookId)).isNotEqualTo(first);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalReservationRetryReturnsPersistedStageWithoutChangingIdentity() {
        long bookId = 90000004L;
        String operationId = UUID.randomUUID().toString();
        assertThat(bulkMetadataJournal.reserve(operationId, "retryable-job", bookId))
                .isEqualTo(BulkMetadataJournalEntity.Stage.PENDING_DB);
        assertThat(bulkMetadataJournal.reserve(operationId, "retryable-job", bookId))
                .isEqualTo(BulkMetadataJournalEntity.Stage.PENDING_DB);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> bulkMetadataJournal.recordDatabaseCommit(operationId));
        assertThat(bulkMetadataJournal.reserve(operationId, "retryable-job", bookId))
                .isEqualTo(BulkMetadataJournalEntity.Stage.DB_COMMITTED);
        assertThatThrownBy(() -> bulkMetadataJournal.reserve(operationId, "other-job", bookId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bulkMetadataJournal.reserve(operationId, "retryable-job", bookId + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bulkMetadataJournal.reserve("invalid", "retryable-job", bookId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bulkMetadataJournal.reserve(UUID.randomUUID().toString(), "other-job", bookId))
                .isInstanceOf(DataIntegrityViolationException.class);
        BulkMetadataJournalEntity entry = bulkMetadataJournalRepository.findById(operationId).orElseThrow();
        assertThat(entry.getStage()).isEqualTo(BulkMetadataJournalEntity.Stage.DB_COMMITTED);
        assertThat(entry.getActiveBookId()).isEqualTo(bookId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalPreparationCommitsIntentBeforeWritingAndKeepsItAfterFailure(@TempDir Path directory)
            throws Exception {
        String oldHash = "a".repeat(64);
        String newHash = "b".repeat(64);
        var intent = new BulkMetadataJournal.EpubPreparationIntent(directory.resolve("source.epub"),
                directory.resolve("candidate.epub"), directory.resolve("backup.epub"), oldHash);
        String operationId = bulkMetadataJournal.enqueue("prepare-test", 90000005L);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> bulkMetadataJournal.recordDatabaseCommit(operationId));
        EpubMetadataWriter writer = mock(EpubMetadataWriter.class);
        var preparation = new BulkMetadataPreparationService(bulkMetadataJournal, writer);
        BookMetadataEntity metadata = new BookMetadataEntity();
        MetadataClearFlags clear = new MetadataClearFlags();
        doAnswer(invocation -> {
            assertThat(bulkMetadataJournalRepository.findById(operationId).orElseThrow().getStage())
                    .isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_PREPARING);
            return newHash;
        }).when(writer).prepareMetadataCandidate(intent.source(), intent.candidate(), intent.backup(), oldHash,
                metadata, clear);

        assertThat(preparation.prepare(operationId, intent, metadata, clear)).isEqualTo(newHash);
        BulkMetadataJournalEntity prepared = bulkMetadataJournalRepository.findById(operationId).orElseThrow();
        assertThat(prepared.getStage()).isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_PREPARED);
        assertThat(prepared.getCandidateSha256()).isEqualTo(newHash);

        String failedId = bulkMetadataJournal.enqueue("prepare-test", 90000006L);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> bulkMetadataJournal.recordDatabaseCommit(failedId));
        doThrow(new IOException("write failed")).when(writer).prepareMetadataCandidate(
                intent.source(), intent.candidate(), intent.backup(), oldHash, metadata, clear);
        assertThatThrownBy(() -> preparation.prepare(failedId, intent, metadata, clear))
                .isInstanceOf(IOException.class);
        BulkMetadataJournalEntity failed = bulkMetadataJournalRepository.findById(failedId).orElseThrow();
        assertThat(failed.getStage()).isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_PREPARING);
        assertThat(failed.getActiveBookId()).isEqualTo(90000006L);
        assertThat(failed.getCandidateSha256()).isNull();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> {
                    try {
                        preparation.prepare(failedId, intent, metadata, clear);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                })).isInstanceOf(IllegalStateException.class)
                .hasMessage("EPUB preparation must start outside a transaction");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalUpdatesDatabaseThenPreparesEpubFromCommittedMetadata(@TempDir Path directory) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long bookId = tx.execute(status -> {
            LibraryEntity library = LibraryEntity.builder().name("Bulk chain").icon("book").watch(false).build();
            entityManager.persist(library);
            BookEntity book = BookEntity.builder().library(library).addedOn(Instant.now()).deleted(false).build();
            book.setMetadata(BookMetadataEntity.builder().book(book).title("Original").build());
            entityManager.persist(book);
            entityManager.flush();
            return book.getId();
        });
        Path source = directory.resolve("book.epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            for (var entry : Map.of(
                    "mimetype", "application/epub+zip",
                    "META-INF/container.xml", """
                            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                              <rootfiles><rootfile full-path="OEBPS/content.opf"
                                  media-type="application/oebps-package+xml"/></rootfiles>
                            </container>""",
                    "OEBPS/content.opf", """
                            <package xmlns="http://www.idpf.org/2007/opf">
                              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                                <dc:title>Original</dc:title>
                              </metadata>
                              <manifest/><spine/>
                            </package>""").entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        byte[] original = Files.readAllBytes(source);
        String oldHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        String operationId = bulkMetadataJournal.enqueue("database-to-epub", bookId);
        bulkMetadataDatabaseService.apply(operationId, bookId, BookMetadata.builder().title("Updated").build(),
                MetadataReplaceMode.REPLACE_WHEN_PROVIDED, false);

        BookMetadataEntity committed = tx.execute(status -> {
            BookMetadataEntity metadata = bookRepository.findAllWithMetadataByIds(Set.of(bookId))
                    .getFirst().getMetadata();
            metadata.getAuthors().size();
            metadata.getCategories().size();
            metadata.getMoods().size();
            metadata.getTags().size();
            return metadata;
        });
        MetadataPersistenceSettings settings = new MetadataPersistenceSettings();
        settings.setSaveToOriginalFile(MetadataPersistenceSettings.SaveToOriginalFile.builder()
                .epub(MetadataPersistenceSettings.FormatSettings.builder().enabled(true).maxFileSizeInMb(100).build())
                .build());
        var writer = new EpubMetadataWriter(() -> settings, new ArchiveService());
        var preparation = new BulkMetadataPreparationService(bulkMetadataJournal, writer);
        Path candidate = directory.resolve("candidate.epub");
        Path backup = directory.resolve("backup.epub");
        var intent = new BulkMetadataJournal.EpubPreparationIntent(source, candidate, backup, oldHash);
        String candidateHash = preparation.prepare(operationId, intent, committed, new MetadataClearFlags());

        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(Files.readAllBytes(backup)).isEqualTo(original);
        try (var zip = new ZipFile(candidate.toFile());
             var input = zip.getInputStream(zip.getEntry("OEBPS/content.opf"))) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).contains("Updated");
        }
        BulkMetadataJournalEntity prepared = bulkMetadataJournalRepository.findById(operationId).orElseThrow();
        assertThat(prepared.getStage()).isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_PREPARED);
        assertThat(prepared.getCandidateSha256()).isEqualTo(candidateHash);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bulkJournalInstallerPreservesBackupAndResolvesLostApplyAcknowledgement(@TempDir Path directory)
            throws Exception {
        byte[] original = "original epub bytes".getBytes(StandardCharsets.UTF_8);
        byte[] candidate = "candidate epub bytes".getBytes(StandardCharsets.UTF_8);
        String oldHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        String newHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(candidate));

        Path first = Files.createDirectory(directory.resolve("first"));
        Path source = first.resolve("source.epub");
        Path backup = first.resolve("backup.epub");
        Path prepared = first.resolve("candidate.epub");
        Files.write(source, original);
        Files.write(backup, original);
        Files.write(prepared, candidate);
        String operationId = bulkMetadataJournal.enqueue("install-test", 90000007L);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> bulkMetadataJournal.recordDatabaseCommit(operationId));
        bulkMetadataJournal.recordEpubPreparationIntent(operationId,
                new BulkMetadataJournal.EpubPreparationIntent(source, prepared, backup, oldHash));
        bulkMetadataJournal.recordEpubPrepared(operationId, newHash);

        assertThat(bulkMetadataEpubInstaller.install(operationId))
                .isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_APPLIED);
        assertThat(Files.readAllBytes(source)).isEqualTo(candidate);
        assertThat(Files.readAllBytes(backup)).isEqualTo(original);
        assertThat(Files.exists(prepared)).isFalse();
        assertThat(bulkMetadataJournalRepository.findById(operationId).orElseThrow().getStage())
                .isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_APPLIED);

        Path second = Files.createDirectory(directory.resolve("second"));
        Path source2 = second.resolve("source.epub");
        Path backup2 = second.resolve("backup.epub");
        Path prepared2 = second.resolve("candidate.epub");
        Files.write(source2, original);
        Files.write(backup2, original);
        Files.write(prepared2, candidate);
        String interrupted = bulkMetadataJournal.enqueue("install-test", 90000008L);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> bulkMetadataJournal.recordDatabaseCommit(interrupted));
        bulkMetadataJournal.recordEpubPreparationIntent(interrupted,
                new BulkMetadataJournal.EpubPreparationIntent(source2, prepared2, backup2, oldHash));
        bulkMetadataJournal.recordEpubPrepared(interrupted, newHash);
        bulkMetadataJournal.markEpubApplyingAfterVerification(interrupted);
        Files.move(prepared2, source2, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        assertThat(bulkMetadataEpubInstaller.install(interrupted))
                .isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_APPLIED);
        assertThat(Files.readAllBytes(source2)).isEqualTo(candidate);
        assertThat(Files.readAllBytes(backup2)).isEqualTo(original);
    }

    @Test
    void bulkRecoveryReportIsPagedAndRejectsUnboundedRequests() {
        String jobId = "paged-recovery";
        bulkMetadataJournal.enqueue(jobId, 90000009L);
        bulkMetadataJournal.enqueue(jobId, 90000010L);
        bulkMetadataJournal.enqueue(jobId, 90000011L);

        var firstPage = bulkMetadataRecoveryService.inspect(jobId, 0, 2);
        var secondPage = bulkMetadataRecoveryService.inspect(jobId, 1, 2);

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent()).allSatisfy(item -> {
            assertThat(item.stage()).isEqualTo(BulkMetadataJournalEntity.Stage.PENDING_DB);
            assertThat(item.files().observation())
                    .isEqualTo(BulkMetadataRecoveryInspector.Observation.NOT_PREPARED);
        });
        assertThatThrownBy(() -> bulkMetadataRecoveryService.inspect(jobId, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bulkMetadataRecoveryService.inspect(jobId, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bulkRecoveryInspectsCopiesWithoutModifyingThem(@TempDir Path directory) throws Exception {
        byte[] original = "original epub".getBytes(StandardCharsets.UTF_8);
        byte[] updated = "updated epub".getBytes(StandardCharsets.UTF_8);
        Path source = directory.resolve("book.epub");
        Path backup = directory.resolve("backup.epub");
        Path candidate = directory.resolve("candidate.epub");
        Files.write(source, original);
        Files.write(backup, original);
        Files.write(candidate, updated);
        var digest = MessageDigest.getInstance("SHA-256");
        String oldHash = HexFormat.of().formatHex(digest.digest(original));
        String newHash = HexFormat.of().formatHex(digest.digest(updated));
        BulkMetadataJournalEntity entry = BulkMetadataJournalEntity.builder()
                .stage(BulkMetadataJournalEntity.Stage.EPUB_PREPARING)
                .sourcePath(source.toString()).backupPath(backup.toString()).candidatePath(candidate.toString())
                .originalSha256(oldHash).build();

        var preparing = BulkMetadataRecoveryInspector.inspect(entry);
        assertThat(preparing.observation()).isEqualTo(BulkMetadataRecoveryInspector.Observation.PREPARATION_INCOMPLETE);
        assertThat(preparing.candidate()).isEqualTo(BulkMetadataRecoveryInspector.FileState.UNVERIFIED);
        Files.delete(candidate);
        Files.delete(backup);
        var absent = BulkMetadataRecoveryInspector.inspect(entry);
        assertThat(absent.observation()).isEqualTo(BulkMetadataRecoveryInspector.Observation.PREPARATION_INCOMPLETE);
        assertThat(absent.candidate()).isEqualTo(BulkMetadataRecoveryInspector.FileState.MISSING);
        assertThat(absent.backup()).isEqualTo(BulkMetadataRecoveryInspector.FileState.MISSING);
        Files.writeString(candidate, "partial");
        assertThat(BulkMetadataRecoveryInspector.inspect(entry).observation())
                .isEqualTo(BulkMetadataRecoveryInspector.Observation.PREPARATION_INCOMPLETE);
        Files.writeString(source, "unexpected");
        assertThat(BulkMetadataRecoveryInspector.inspect(entry).observation())
                .isEqualTo(BulkMetadataRecoveryInspector.Observation.CONFLICT);
        Files.write(source, original);
        Files.writeString(backup, "unexpected");
        assertThat(BulkMetadataRecoveryInspector.inspect(entry).observation())
                .isEqualTo(BulkMetadataRecoveryInspector.Observation.CONFLICT);
        Files.write(backup, original);
        Files.write(candidate, updated);
        entry.setStage(BulkMetadataJournalEntity.Stage.EPUB_PREPARED);
        entry.setCandidateSha256(newHash);
        assertThat(BulkMetadataRecoveryInspector.inspect(entry).observation())
                .isEqualTo(BulkMetadataRecoveryInspector.Observation.ORIGINAL_PRESENT);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        entry.setStage(BulkMetadataJournalEntity.Stage.EPUB_APPLYING);
        Files.write(source, updated);
        Files.delete(candidate);
        var installed = BulkMetadataRecoveryInspector.inspect(entry);
        assertThat(installed.observation()).isEqualTo(BulkMetadataRecoveryInspector.Observation.CANDIDATE_PRESENT);
        assertThat(installed.candidate()).isEqualTo(BulkMetadataRecoveryInspector.FileState.MISSING);

        Files.writeString(backup, "unexpected");
        entry.setStage(BulkMetadataJournalEntity.Stage.NEEDS_ATTENTION);
        entry.setPreviousStage(BulkMetadataJournalEntity.Stage.EPUB_APPLYING);
        var conflict = BulkMetadataRecoveryInspector.inspect(entry);
        assertThat(conflict.observation()).isEqualTo(BulkMetadataRecoveryInspector.Observation.CONFLICT);
        assertThat(conflict.backup()).isEqualTo(BulkMetadataRecoveryInspector.FileState.DIFFERENT);
        assertThat(Files.readAllBytes(source)).isEqualTo(updated);
    }

    @Test
    void metadataScorePreviewCountsOnlyActiveBooksInTheSelectedLibrary() {
        LibraryEntity library = LibraryEntity.builder().name("Preview").icon("book").watch(false).build();
        LibraryEntity other = LibraryEntity.builder().name("Other").icon("book").watch(false).build();
        entityManager.persist(library);
        entityManager.persist(other);
        List<BookEntity> books = new ArrayList<>();
        for (Float score : new Float[]{0f, 89.99f, 90f, 100f, null, -1f, 101f}) {
            BookEntity book = BookEntity.builder().library(library).metadataMatchScore(score)
                    .addedOn(Instant.now()).deleted(false).build();
            entityManager.persist(book);
            books.add(book);
        }
        entityManager.persist(BookEntity.builder().library(library).metadataMatchScore(1f)
                .addedOn(Instant.now()).deleted(true).build());
        entityManager.persist(BookEntity.builder().library(other).metadataMatchScore(1f)
                .addedOn(Instant.now()).deleted(false).build());
        entityManager.flush();

        var result = bookRepository.previewMetadataScores(library.getId(), 90f);
        assertThat(result.getTotal()).isEqualTo(7);
        assertThat(result.getBelowThreshold()).isEqualTo(2);
        assertThat(result.getAtOrAboveThreshold()).isEqualTo(2);
        assertThat(result.getUnknown()).isEqualTo(3);

        assertThat(bookRepository.findBookIdsBelowMetadataThreshold(library.getId(), 90f))
                .containsExactlyInAnyOrder(books.get(0).getId(), books.get(1).getId(),
                        books.get(4).getId(), books.get(5).getId(), books.get(6).getId());
        assertThat(bookRepository.findBookIdsBelowMetadataThreshold(library.getId(), 0f))
                .containsExactlyInAnyOrder(books.get(4).getId(), books.get(5).getId(), books.get(6).getId());
        assertThat(bookRepository.findBookIdsBelowMetadataThreshold(library.getId(), 100f))
                .containsExactlyInAnyOrder(books.get(0).getId(), books.get(1).getId(), books.get(2).getId(),
                        books.get(4).getId(), books.get(5).getId(), books.get(6).getId());

        var empty = bookRepository.previewMetadataScores(-1L, 90f);
        assertThat(empty.getTotal()).isZero();
        assertThat(empty.getBelowThreshold()).isZero();
        assertThat(empty.getAtOrAboveThreshold()).isZero();
        assertThat(empty.getUnknown()).isZero();
    }

    @Test
    void metadataScorePreviewRequiresPermissionAndLibraryAccessAndValidatesInput() {
        LibraryEntity library = LibraryEntity.builder().name("Access test").icon("book").watch(false).build();
        entityManager.persist(library);
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        BookLoreUser user = BookLoreUser.builder().id(-1L).permissions(permissions)
                .assignedLibraries(List.of(Library.builder().id(library.getId()).build())).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        try {
            assertThatThrownBy(() -> metadataController.previewRefresh(library.getId(), 90f))
                    .isInstanceOf(AccessDeniedException.class);
            permissions.setCanBulkAutoFetchMetadata(true);
            assertThat(metadataController.previewRefresh(library.getId(), 90f).getBody().total()).isZero();
            user.setAssignedLibraries(List.of());
            assertThatExceptionOfType(APIException.class)
                    .isThrownBy(() -> metadataController.previewRefresh(library.getId(), 90f))
                    .satisfies(e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            permissions.setAdmin(true);
            for (float threshold : new float[]{-1f, 101f, Float.NaN, Float.POSITIVE_INFINITY}) {
                assertThatExceptionOfType(APIException.class)
                        .isThrownBy(() -> metadataController.previewRefresh(library.getId(), threshold))
                        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            }
            assertThat(metadataController.previewRefresh(library.getId(), 0f).getBody().total()).isZero();
            assertThat(metadataController.previewRefresh(library.getId(), 100f).getBody().total()).isZero();
            assertThatExceptionOfType(APIException.class)
                    .isThrownBy(() -> metadataController.previewRefresh(-1L, 90f))
                    .satisfies(e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void metadataRefreshFailureUpdatesPersistedJob() {
        MetadataFetchJobEntity job = MetadataFetchJobEntity.builder()
                .taskId("failed-refresh")
                .status(MetadataFetchTaskStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .totalBooksCount(3)
                .completedBooks(0)
                .build();
        entityManager.persist(job);
        entityManager.flush();
        Instant completedAt = Instant.now();

        assertThat(metadataFetchJobRepository.updateFailure("failed-refresh", MetadataFetchTaskStatus.ERROR,
                "Reservation unavailable", completedAt, 1)).isEqualTo(1);
        entityManager.clear();

        MetadataFetchJobEntity persisted = entityManager.find(MetadataFetchJobEntity.class, "failed-refresh");
        assertThat(persisted.getStatus()).isEqualTo(MetadataFetchTaskStatus.ERROR);
        assertThat(persisted.getStatusMessage()).isEqualTo("Reservation unavailable");
        assertThat(persisted.getCompletedAt()).isNotNull();
        assertThat(persisted.getCompletedBooks()).isEqualTo(1);
    }

    @Test
    void repairLookupReloadsOnlyActiveEpubBookFiles() {
        LibraryEntity library = LibraryEntity.builder().name("Repair test").icon("book").watch(false).build();
        entityManager.persist(library);
        LibraryPathEntity path = LibraryPathEntity.builder().library(library).path("/repair").build();
        entityManager.persist(path);
        BookEntity active = BookEntity.builder().library(library).libraryPath(path).addedOn(Instant.now()).deleted(false).build();
        BookEntity deleted = BookEntity.builder().library(library).libraryPath(path).addedOn(Instant.now()).deleted(true).build();
        entityManager.persist(active);
        entityManager.persist(deleted);
        BookFileEntity epub = BookFileEntity.builder().book(active).fileName("active.epub").fileSubPath("")
                .isBookFormat(true).bookType(BookFileType.EPUB).fileSizeKb(1L)
                .initialHash("hash").currentHash("hash").addedOn(Instant.now()).build();
        BookFileEntity pdf = BookFileEntity.builder().book(active).fileName("other.pdf").fileSubPath("")
                .isBookFormat(true).bookType(BookFileType.PDF).fileSizeKb(1L)
                .initialHash("hash").currentHash("hash").addedOn(Instant.now()).build();
        BookFileEntity deletedEpub = BookFileEntity.builder().book(deleted).fileName("deleted.epub").fileSubPath("")
                .isBookFormat(true).bookType(BookFileType.EPUB).fileSizeKb(1L)
                .initialHash("hash").currentHash("hash").addedOn(Instant.now()).build();
        BookFileEntity attachment = BookFileEntity.builder().book(active).fileName("attachment.epub").fileSubPath("")
                .isBookFormat(false).bookType(BookFileType.EPUB).fileSizeKb(1L)
                .initialHash("hash").currentHash("hash").addedOn(Instant.now()).build();
        entityManager.persist(epub);
        entityManager.persist(pdf);
        entityManager.persist(deletedEpub);
        entityManager.persist(attachment);
        entityManager.flush();
        var ids = List.of(epub.getId(), pdf.getId(), deletedEpub.getId(), attachment.getId());
        entityManager.clear();

        assertThat(bookFileRepository.findAllForRepairByIdIn(ids, BookFileType.EPUB))
                .extracting(BookFileEntity::getId).containsExactly(epub.getId());
    }

    @Test
    void findAllWithMetadataByIds_executesAgainstJpaMetamodel() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.PDF))
                .build();
        entityManager.persist(library);
        entityManager.flush();

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/test/path")
                .build();
        entityManager.persist(libraryPath);
        entityManager.flush();

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
                .fileSizeKb(500L)
                .initialHash("hash1")
                .currentHash("hash1")
                .addedOn(Instant.now())
                .build();
        entityManager.persist(bookFile);
        entityManager.flush();

        entityManager.clear();

        Optional<BookEntity> result = bookRepository.findByIdForKoboDownload(book.getId());

        TestTransaction.end();

        assertThat(result).isPresent();

        BookEntity bookEntity = result.get();
        assertThat(bookEntity.getId()).isEqualTo(book.getId());
        assertThat(bookEntity.getPrimaryBookFile()).isNotNull();
    }

    @Test
    void findByIdForBulkMetadataEpubUpdate_eagerlyLoadsFilesForDetachedUse() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Bulk EPUB update")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB))
                .build();
        entityManager.persist(library);
        entityManager.flush();

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/test/path")
                .build();
        entityManager.persist(libraryPath);

        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(book);
        BookMetadataEntity metadata = BookMetadataEntity.builder().book(book).title("Bulk EPUB").build();
        entityManager.persist(metadata);

        BookFileEntity bookFile = BookFileEntity.builder()
                .book(book)
                .fileName("bulk.epub")
                .fileSubPath("")
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .fileSizeKb(1L)
                .initialHash("hash")
                .currentHash("hash")
                .addedOn(Instant.now())
                .build();
        entityManager.persist(bookFile);
        entityManager.flush();
        Long bookId = book.getId();
        entityManager.clear();

        BookEntity loaded = bookRepository.findByIdForBulkMetadataEpubUpdate(bookId).orElseThrow();

        assertThat(org.hibernate.Hibernate.isInitialized(loaded.getBookFiles())).isTrue();
        assertThat(loaded.getPrimaryBookFile().getFileName()).isEqualTo("bulk.epub");
    }

    private record LibraryFixture(Long libraryId, List<Long> bookIds, List<Long> comicBookIds) {
    }

    private LibraryFixture persistLibraryWithBooksAndComics() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .formatPriority(List.of(BookFileType.EPUB, BookFileType.CBX))
                .build();
        entityManager.persist(library);

        List<Long> bookIds = new ArrayList<>();
        List<Long> comicBookIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            BookEntity book = BookEntity.builder().library(library).addedOn(Instant.now()).deleted(false).build();
            entityManager.persist(book);
            BookMetadataEntity metadata = BookMetadataEntity.builder().book(book).title("Book " + i).build();
            entityManager.persist(metadata);
            entityManager.flush();
            bookIds.add(book.getId());
            if (i % 2 == 0) {
                entityManager.persist(ComicMetadataEntity.builder()
                        .bookId(book.getId())
                        .bookMetadata(metadata)
                        .issueNumber("Issue " + book.getId())
                        .build());
                comicBookIds.add(book.getId());
            }
        }
        entityManager.flush();
        entityManager.clear();
        return new LibraryFixture(library.getId(), bookIds, comicBookIds);
    }

    private void assertLoadsComicMetadataInOneStatement(String query, Supplier<List<BookEntity>> load, LibraryFixture fixture) {
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<BookEntity> books = load.get();

        assertThat(books).as(query).extracting(BookEntity::getId).containsExactlyInAnyOrderElementsOf(fixture.bookIds());
        for (BookEntity book : books) {
            ComicMetadataEntity comic = book.getMetadata().getComicMetadata();
            assertThat(comic == null ? null : comic.getIssueNumber())
                    .as(query + " book " + book.getId())
                    .isEqualTo(fixture.comicBookIds().contains(book.getId()) ? "Issue " + book.getId() : null);
        }
        assertThat(statistics.getPrepareStatementCount()).as(query + " statements").isEqualTo(1);
    }

    @Test
    void bookQueries_loadComicMetadataWithTheBooks() {
        LibraryFixture fixture = persistLibraryWithBooksAndComics();
        Long libraryId = fixture.libraryId();

        Map<String, Supplier<List<BookEntity>>> queries = Map.of(
                "findAllWithMetadata", bookRepository::findAllWithMetadata,
                "findAllWithMetadataByIds", () -> bookRepository.findAllWithMetadataByIds(new HashSet<>(fixture.bookIds())),
                "findAllWithMetadataByLibraryIds", () -> bookRepository.findAllWithMetadataByLibraryIds(List.of(libraryId)),
                "findAllWithMetadataPage", () -> bookRepository.findAllWithMetadataPage(PageRequest.of(0, 50)).getContent(),
                "findAllWithMetadataByLibraryId", () -> bookRepository.findAllWithMetadataByLibraryId(libraryId),
                "findAllByLibraryIdWithFiles", () -> bookRepository.findAllByLibraryIdWithFiles(libraryId),
                "findAllForDuplicateDetection", () -> bookRepository.findAllForDuplicateDetection(libraryId),
                "findAllForRecommendation", () -> bookRepository.findAllForRecommendation(-1L),
                "opds findAllWithMetadataByIds", () -> bookOpdsRepository.findAllWithMetadataByIds(fixture.bookIds()),
                "opds findAllWithFullMetadataByIds", () -> bookOpdsRepository.findAllWithFullMetadataByIds(fixture.bookIds()));

        queries.forEach((query, load) -> assertLoadsComicMetadataInOneStatement(query, load, fixture));
    }
}
