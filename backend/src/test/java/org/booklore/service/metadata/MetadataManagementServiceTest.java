package org.booklore.service.metadata;

import org.booklore.config.AppProperties;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MergeMetadataType;
import org.booklore.repository.*;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.booklore.service.file.FileFingerprint;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataManagementServiceTest {

    @Mock private AppProperties appProperties;
    @Mock private AuthorRepository authorRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MoodRepository moodRepository;
    @Mock private TagRepository tagRepository;
    @Mock private BookMetadataRepository bookMetadataRepository;
    @Mock private AppSettingService appSettingService;
    @Mock private MetadataWriterFactory metadataWriterFactory;
    @Mock private FileMoveService fileMoveService;
    @Mock private BookRepository bookRepository;

    @InjectMocks
    private MetadataManagementService service;

    @BeforeEach
    void setUp() {
        lenient().when(appProperties.isLocalStorage()).thenReturn(true);
        lenient().when(appSettingService.getAppSettings()).thenReturn(
                AppSettings.builder()
                        .metadataPersistenceSettings(MetadataPersistenceSettings.builder()
                                .moveFilesToLibraryPattern(false)
                                .build())
                        .build()
        );
    }

    @Test
    void consolidateAuthors_mergesOldIntoTargetAndDeletesOld() {
        AuthorEntity oldAuthor = AuthorEntity.builder().id(1L).name("Old Author").build();
        AuthorEntity targetAuthor = AuthorEntity.builder().id(2L).name("Target Author").build();

        when(authorRepository.findByNameIgnoreCase("Target Author")).thenReturn(Optional.of(targetAuthor));
        when(authorRepository.findByNameIgnoreCase("Old Author")).thenReturn(Optional.of(oldAuthor));

        List<AuthorEntity> authors = new ArrayList<>(List.of(oldAuthor));
        BookMetadataEntity metadata = BookMetadataEntity.builder().authors(authors).build();
        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target Author"), List.of("Old Author"));

        assertThat(metadata.getAuthors()).contains(targetAuthor);
        assertThat(metadata.getAuthors()).doesNotContain(oldAuthor);
        verify(authorRepository).delete(oldAuthor);
        verify(bookMetadataRepository).saveAll(anyList());
    }

    @Test
    void consolidateAuthors_createsNewAuthorWhenTargetDoesNotExist() {
        AuthorEntity oldAuthor = AuthorEntity.builder().id(1L).name("Old").build();
        AuthorEntity newAuthor = AuthorEntity.builder().id(3L).name("New Author").build();

        when(authorRepository.findByNameIgnoreCase("New Author")).thenReturn(Optional.empty());
        when(authorRepository.save(any(AuthorEntity.class))).thenReturn(newAuthor);
        when(authorRepository.findByNameIgnoreCase("Old")).thenReturn(Optional.of(oldAuthor));

        List<AuthorEntity> authors = new ArrayList<>(List.of(oldAuthor));
        BookMetadataEntity metadata = BookMetadataEntity.builder().authors(authors).build();
        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.authors, List.of("New Author"), List.of("Old"));

        verify(authorRepository).delete(oldAuthor);
    }

    @Test
    void consolidateCategories_mergesAndDeletesOld() {
        CategoryEntity oldCat = CategoryEntity.builder().id(1L).name("Old Cat").build();
        CategoryEntity targetCat = CategoryEntity.builder().id(2L).name("Target Cat").build();

        when(categoryRepository.findByNameIgnoreCase("Target Cat")).thenReturn(Optional.of(targetCat));
        when(categoryRepository.findByNameIgnoreCase("Old Cat")).thenReturn(Optional.of(oldCat));

        Set<CategoryEntity> categories = new HashSet<>(List.of(oldCat));
        BookMetadataEntity metadata = BookMetadataEntity.builder().categories(categories).build();
        when(bookMetadataRepository.findAllByCategoriesContaining(oldCat)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.categories, List.of("Target Cat"), List.of("Old Cat"));

        assertThat(metadata.getCategories()).contains(targetCat);
        verify(categoryRepository).delete(oldCat);
    }

    @Test
    void consolidateMoods_mergesAndDeletesOld() {
        MoodEntity oldMood = MoodEntity.builder().id(1L).name("Old Mood").build();
        MoodEntity targetMood = MoodEntity.builder().id(2L).name("Target Mood").build();

        when(moodRepository.findByNameIgnoreCase("Target Mood")).thenReturn(Optional.of(targetMood));
        when(moodRepository.findByNameIgnoreCase("Old Mood")).thenReturn(Optional.of(oldMood));

        Set<MoodEntity> moods = new HashSet<>(List.of(oldMood));
        BookMetadataEntity metadata = BookMetadataEntity.builder().moods(moods).build();
        when(bookMetadataRepository.findAllByMoodsContaining(oldMood)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.moods, List.of("Target Mood"), List.of("Old Mood"));

        assertThat(metadata.getMoods()).contains(targetMood);
        verify(moodRepository).delete(oldMood);
    }

    @Test
    void consolidateTags_mergesAndDeletesOld() {
        TagEntity oldTag = TagEntity.builder().id(1L).name("Old Tag").build();
        TagEntity targetTag = TagEntity.builder().id(2L).name("Target Tag").build();

        when(tagRepository.findByNameIgnoreCase("Target Tag")).thenReturn(Optional.of(targetTag));
        when(tagRepository.findByNameIgnoreCase("Old Tag")).thenReturn(Optional.of(oldTag));

        Set<TagEntity> tags = new HashSet<>(List.of(oldTag));
        BookMetadataEntity metadata = BookMetadataEntity.builder().tags(tags).build();
        when(bookMetadataRepository.findAllByTagsContaining(oldTag)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.tags, List.of("Target Tag"), List.of("Old Tag"));

        assertThat(metadata.getTags()).contains(targetTag);
        verify(tagRepository).delete(oldTag);
    }

    @Test
    void consolidateSeries_updatesSeriesNameOnAllBooks() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().seriesName("Old Series").build();
        when(bookMetadataRepository.findAllBySeriesNameIgnoreCase("Old Series")).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.series, List.of("New Series"), List.of("Old Series"));

        assertThat(metadata.getSeriesName()).isEqualTo("New Series");
        verify(bookMetadataRepository).saveAll(List.of(metadata));
    }

    @Test
    void consolidateSeries_throwsWhenMultipleTargetValues() {
        assertThatThrownBy(() ->
                service.consolidateMetadata(MergeMetadataType.series, List.of("A", "B"), List.of("Old"))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one target value");
    }

    @Test
    void consolidatePublishers_updatesPublisherOnAllBooks() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().publisher("Old Pub").build();
        when(bookMetadataRepository.findAllByPublisherIgnoreCase("Old Pub")).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.publishers, List.of("New Pub"), List.of("Old Pub"));

        assertThat(metadata.getPublisher()).isEqualTo("New Pub");
    }

    @Test
    void consolidatePublishers_throwsWhenMultipleTargetValues() {
        assertThatThrownBy(() ->
                service.consolidateMetadata(MergeMetadataType.publishers, List.of("A", "B"), List.of("Old"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consolidateLanguages_updatesLanguageOnAllBooks() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().language("fr").build();
        when(bookMetadataRepository.findAllByLanguageIgnoreCase("fr")).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.languages, List.of("en"), List.of("fr"));

        assertThat(metadata.getLanguage()).isEqualTo("en");
    }

    @Test
    void consolidateLanguages_throwsWhenMultipleTargetValues() {
        assertThatThrownBy(() ->
                service.consolidateMetadata(MergeMetadataType.languages, List.of("en", "fr"), List.of("de"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consolidateAuthors_writesMetadataToFileWhenWriterPresent() throws Exception {
        AuthorEntity oldAuthor = AuthorEntity.builder().id(1L).name("Old").build();
        AuthorEntity targetAuthor = AuthorEntity.builder().id(2L).name("Target").build();

        when(authorRepository.findByNameIgnoreCase("Target")).thenReturn(Optional.of(targetAuthor));
        when(authorRepository.findByNameIgnoreCase("Old")).thenReturn(Optional.of(oldAuthor));

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-");
        java.nio.file.Path subDir = tempDir.resolve("sub");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path tempFile = subDir.resolve("test.epub");
        java.nio.file.Files.createFile(tempFile);

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileName("test.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        BookEntity book = BookEntity.builder()
                .id(1L)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .authors(new ArrayList<>(List.of(oldAuthor)))
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));

        MetadataWriter writer = mock(MetadataWriter.class);
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.of(writer));

        try (MockedStatic<FileFingerprint> ffMock = mockStatic(FileFingerprint.class)) {
            ffMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("newhash");

            service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));
        }

        verify(writer).saveMetadataToFile(any(), eq(metadata), isNull(), isNull());
        verify(bookRepository).saveAndFlush(book);

        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(subDir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void consolidateAuthors_movesFileWhenEnabled() throws Exception {
        when(appSettingService.getAppSettings()).thenReturn(
                AppSettings.builder()
                        .metadataPersistenceSettings(MetadataPersistenceSettings.builder()
                                .moveFilesToLibraryPattern(true)
                                .build())
                        .build()
        );

        AuthorEntity oldAuthor = AuthorEntity.builder().id(1L).name("Old").build();
        AuthorEntity targetAuthor = AuthorEntity.builder().id(2L).name("Target").build();

        when(authorRepository.findByNameIgnoreCase("Target")).thenReturn(Optional.of(targetAuthor));
        when(authorRepository.findByNameIgnoreCase("Old")).thenReturn(Optional.of(oldAuthor));

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-move-");
        java.nio.file.Path subDir = tempDir.resolve("sub");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path tempFile = subDir.resolve("test.epub");
        java.nio.file.Files.createFile(tempFile);

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileName("test.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        BookEntity book = BookEntity.builder()
                .id(1L)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .authors(new ArrayList<>(List.of(oldAuthor)))
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.empty());
        when(fileMoveService.moveSingleFile(book)).thenReturn(
                FileMoveResult.builder().moved(true).newFileName("new.epub").newFileSubPath("new/sub").build()
        );

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));

        assertThat(bookFile.getFileName()).isEqualTo("new.epub");
        assertThat(bookFile.getFileSubPath()).isEqualTo("new/sub");
        verify(bookRepository).saveAndFlush(book);

        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(subDir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void deleteAuthors_removesFromBooksAndDeletesEntity() {
        AuthorEntity author = AuthorEntity.builder().id(1L).name("Author1").build();
        when(authorRepository.findByName("Author1")).thenReturn(Optional.of(author));

        List<AuthorEntity> authors = new ArrayList<>(List.of(author));
        BookMetadataEntity metadata = BookMetadataEntity.builder().authors(authors).build();
        when(bookMetadataRepository.findAllByAuthorsContaining(author)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.authors, List.of("Author1"));

        assertThat(metadata.getAuthors()).isEmpty();
        verify(authorRepository).delete(author);
    }

    @Test
    void deleteCategories_removesFromBooksAndDeletesEntity() {
        CategoryEntity category = CategoryEntity.builder().id(1L).name("Cat1").build();
        when(categoryRepository.findByNameIgnoreCase("Cat1")).thenReturn(Optional.of(category));

        Set<CategoryEntity> categories = new HashSet<>(List.of(category));
        BookMetadataEntity metadata = BookMetadataEntity.builder().categories(categories).build();
        when(bookMetadataRepository.findAllByCategoriesContaining(category)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.categories, List.of("Cat1"));

        assertThat(metadata.getCategories()).isEmpty();
        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteMoods_removesFromBooksAndDeletesEntity() {
        MoodEntity mood = MoodEntity.builder().id(1L).name("Mood1").build();
        when(moodRepository.findByNameIgnoreCase("Mood1")).thenReturn(Optional.of(mood));

        Set<MoodEntity> moods = new HashSet<>(List.of(mood));
        BookMetadataEntity metadata = BookMetadataEntity.builder().moods(moods).build();
        when(bookMetadataRepository.findAllByMoodsContaining(mood)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.moods, List.of("Mood1"));

        assertThat(metadata.getMoods()).isEmpty();
        verify(moodRepository).delete(mood);
    }

    @Test
    void deleteTags_removesFromBooksAndDeletesEntity() {
        TagEntity tag = TagEntity.builder().id(1L).name("Tag1").build();
        when(tagRepository.findByNameIgnoreCase("Tag1")).thenReturn(Optional.of(tag));

        Set<TagEntity> tags = new HashSet<>(List.of(tag));
        BookMetadataEntity metadata = BookMetadataEntity.builder().tags(tags).build();
        when(bookMetadataRepository.findAllByTagsContaining(tag)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.tags, List.of("Tag1"));

        assertThat(metadata.getTags()).isEmpty();
        verify(tagRepository).delete(tag);
    }

    @Test
    void deleteSeries_clearsSeriesFieldsFromBooks() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .seriesName("My Series")
                .seriesNumber(1.0f)
                .seriesTotal(3)
                .build();
        when(bookMetadataRepository.findAllBySeriesNameIgnoreCase("My Series")).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.series, List.of("My Series"));

        assertThat(metadata.getSeriesName()).isNull();
        assertThat(metadata.getSeriesNumber()).isNull();
        assertThat(metadata.getSeriesTotal()).isNull();
    }

    @Test
    void deletePublishers_clearsPublisherFromBooks() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().publisher("Old Pub").build();
        when(bookMetadataRepository.findAllByPublisherIgnoreCase("Old Pub")).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.publishers, List.of("Old Pub"));

        assertThat(metadata.getPublisher()).isNull();
    }

    @Test
    void deleteLanguages_clearsLanguageFromBooks() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().language("fr").build();
        when(bookMetadataRepository.findAllByLanguageIgnoreCase("fr")).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.languages, List.of("fr"));

        assertThat(metadata.getLanguage()).isNull();
    }

    @Test
    void deleteSeries_skipsWhenNoBooksFound() {
        when(bookMetadataRepository.findAllBySeriesNameIgnoreCase("Missing")).thenReturn(List.of());

        service.deleteMetadata(MergeMetadataType.series, List.of("Missing"));

        verify(bookMetadataRepository, never()).saveAll(anyList());
    }

    @Test
    void deletePublishers_skipsWhenNoBooksFound() {
        when(bookMetadataRepository.findAllByPublisherIgnoreCase("Missing")).thenReturn(List.of());

        service.deleteMetadata(MergeMetadataType.publishers, List.of("Missing"));

        verify(bookMetadataRepository, never()).saveAll(anyList());
    }

    @Test
    void consolidateAuthors_skipsNonExistentMergeValues() {
        AuthorEntity targetAuthor = AuthorEntity.builder().id(2L).name("Target").build();
        when(authorRepository.findByNameIgnoreCase("Target")).thenReturn(Optional.of(targetAuthor));
        when(authorRepository.findByNameIgnoreCase("NonExistent")).thenReturn(Optional.empty());

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("NonExistent"));

        verify(bookMetadataRepository, never()).findAllByAuthorsContaining(any());
        verify(authorRepository, never()).delete(any());
    }

    @Test
    void writeMetadataToFile_skipsWhenBookIsNull() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().book(null).build();
        when(bookMetadataRepository.findAllBySeriesNameIgnoreCase("Old")).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.series, List.of("New"), List.of("Old"));

        verify(metadataWriterFactory, never()).getWriter(any());
        verify(bookRepository, never()).saveAndFlush(any());
    }

    @Test
    void writeMetadataToFile_skipsWriterWhenNoneAvailable() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-skip-");
        java.nio.file.Path subDir = tempDir.resolve("sub");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path tempFile = subDir.resolve("test.epub");
        java.nio.file.Files.createFile(tempFile);

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileName("test.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        BookEntity book = BookEntity.builder()
                .id(1L)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .seriesName("Old")
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(bookMetadataRepository.findAllBySeriesNameIgnoreCase("Old")).thenReturn(List.of(metadata));
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.empty());

        service.consolidateMetadata(MergeMetadataType.series, List.of("New"), List.of("Old"));

        verify(bookRepository, never()).saveAndFlush(any());

        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(subDir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void consolidateAuthors_fileMoveNotMovedDoesNotUpdateFileName() throws Exception {
        when(appSettingService.getAppSettings()).thenReturn(
                AppSettings.builder()
                        .metadataPersistenceSettings(MetadataPersistenceSettings.builder()
                                .moveFilesToLibraryPattern(true)
                                .build())
                        .build()
        );

        AuthorEntity oldAuthor = AuthorEntity.builder().id(1L).name("Old").build();
        AuthorEntity targetAuthor = AuthorEntity.builder().id(2L).name("Target").build();

        when(authorRepository.findByNameIgnoreCase("Target")).thenReturn(Optional.of(targetAuthor));
        when(authorRepository.findByNameIgnoreCase("Old")).thenReturn(Optional.of(oldAuthor));

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test-metadata-nomove-");
        java.nio.file.Path subDir = tempDir.resolve("sub");
        java.nio.file.Files.createDirectories(subDir);
        java.nio.file.Path tempFile = subDir.resolve("original.epub");
        java.nio.file.Files.createFile(tempFile);

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileName("original.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        BookEntity book = BookEntity.builder()
                .id(1L)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .authors(new ArrayList<>(List.of(oldAuthor)))
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.empty());
        when(fileMoveService.moveSingleFile(book)).thenReturn(
                FileMoveResult.builder().moved(false).build()
        );

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));

        assertThat(bookFile.getFileName()).isEqualTo("original.epub");
        verify(bookRepository, never()).saveAndFlush(any());

        java.nio.file.Files.deleteIfExists(tempFile);
        java.nio.file.Files.deleteIfExists(subDir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test
    void consolidateAuthors_networkStorage_skipsFileWrite() {
        when(appProperties.isLocalStorage()).thenReturn(false);

        AuthorEntity oldAuthor = AuthorEntity.builder().id(1L).name("Old").build();
        AuthorEntity targetAuthor = AuthorEntity.builder().id(2L).name("Target").build();

        when(authorRepository.findByNameIgnoreCase("Target")).thenReturn(Optional.of(targetAuthor));
        when(authorRepository.findByNameIgnoreCase("Old")).thenReturn(Optional.of(oldAuthor));

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileName("test.epub")
                .fileSubPath("sub")
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/fake/path");
        BookEntity book = BookEntity.builder()
                .id(1L)
                .bookFiles(new ArrayList<>(List.of(bookFile)))
                .libraryPath(libraryPath)
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .authors(new ArrayList<>(List.of(oldAuthor)))
                .book(book)
                .build();
        book.setMetadata(metadata);

        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));

        verify(metadataWriterFactory, never()).getWriter(any());
        verify(authorRepository).delete(oldAuthor);
    }

    @Test
    void consolidateAuthors_physicalBook_skipsFileWriteAndDoesNotThrow() {
        AuthorEntity oldAuthor = AuthorEntity.builder().id(1L).name("Old").build();
        AuthorEntity targetAuthor = AuthorEntity.builder().id(2L).name("Target").build();

        when(authorRepository.findByNameIgnoreCase("Target")).thenReturn(Optional.of(targetAuthor));
        when(authorRepository.findByNameIgnoreCase("Old")).thenReturn(Optional.of(oldAuthor));

        BookEntity physicalBook = BookEntity.builder()
                .id(1L)
                .isPhysical(true)
                .bookFiles(new ArrayList<>())
                .build();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .authors(new ArrayList<>(List.of(oldAuthor)))
                .book(physicalBook)
                .build();
        physicalBook.setMetadata(metadata);

        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.authors, List.of("Target"), List.of("Old"));

        assertThat(metadata.getAuthors()).contains(targetAuthor);
        assertThat(metadata.getAuthors()).doesNotContain(oldAuthor);
        verify(metadataWriterFactory, never()).getWriter(any());
        verify(bookRepository, never()).saveAndFlush(any());
        verify(authorRepository).delete(oldAuthor);
    }
}
