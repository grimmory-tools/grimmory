package org.booklore.service.metadata;

import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.*;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMetadataUpdaterTest {

    @Mock
    private AuthorRepository authorRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private ComicMetadataRepository comicMetadataRepository;
    @Mock
    private ComicCharacterRepository comicCharacterRepository;
    @Mock
    private ComicTeamRepository comicTeamRepository;
    @Mock
    private ComicLocationRepository comicLocationRepository;
    @Mock
    private ComicCreatorRepository comicCreatorRepository;
    @Mock
    private FileService fileService;
    @Mock
    private MetadataMatchService metadataMatchService;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private MetadataWriterFactory metadataWriterFactory;
    @Mock
    private BookReviewUpdateService bookReviewUpdateService;
    @Mock
    private FileMoveService fileMoveService;
    @Mock
    private SidecarMetadataWriter sidecarMetadataWriter;

    @InjectMocks
    private BookMetadataUpdater bookMetadataUpdater;

    @BeforeEach
    void setUp() {
        AppSettings appSettings = new AppSettings();
        MetadataPersistenceSettings persistenceSettings = new MetadataPersistenceSettings();

        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = Mockito.mock(MetadataPersistenceSettings.SaveToOriginalFile.class);
        when(saveToOriginalFile.isAnyFormatEnabled()).thenReturn(false);
        persistenceSettings.setSaveToOriginalFile(saveToOriginalFile);

        appSettings.setMetadataPersistenceSettings(persistenceSettings);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    @Test
    void setBookMetadata_withMergeTagsFalse_shouldReplaceTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<TagEntity> existingTags = new HashSet<>();
        existingTags.add(TagEntity.builder().name("Tag1").build());
        existingTags.add(TagEntity.builder().name("Tag2").build());
        metadataEntity.setTags(existingTags);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Set.of("Tag1"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(tagRepository.findByName("Tag1")).thenReturn(Optional.of(TagEntity.builder().name("Tag1").build()));

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals(1, bookEntity.getMetadata().getTags().size());
        assertTrue(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag1")));
        assertFalse(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag2")));
    }

    @Test
    void setBookMetadata_withMergeTagsFalse_andEmptyIncomingSet_shouldClearAllTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<TagEntity> existingTags = new HashSet<>();
        existingTags.add(TagEntity.builder().name("Tag1").build());
        existingTags.add(TagEntity.builder().name("Tag2").build());
        metadataEntity.setTags(existingTags);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Collections.emptySet());

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertTrue(bookEntity.getMetadata().getTags().isEmpty(), "All tags should be cleared when incoming set is empty");
    }

    @Test
    void setBookMetadata_withMergeTagsTrue_shouldMergeTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<TagEntity> existingTags = new HashSet<>();
        existingTags.add(TagEntity.builder().name("Tag1").build());
        existingTags.add(TagEntity.builder().name("Tag2").build());
        metadataEntity.setTags(existingTags);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Set.of("Tag3"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(true)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(tagRepository.findByName("Tag3")).thenReturn(Optional.of(TagEntity.builder().name("Tag3").build()));

        // Act
        bookMetadataUpdater.setBookMetadata(context);

        // Assert
        assertEquals(3, bookEntity.getMetadata().getTags().size()); // Tag1, Tag2, Tag3
        assertTrue(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag1")));
        assertTrue(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag2")));
        assertTrue(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag3")));
    }

    @Test
    void setBookMetadata_withMergeMoodsFalse_shouldReplaceMoods() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<MoodEntity> existingMoods = new HashSet<>();
        existingMoods.add(MoodEntity.builder().name("Mood1").build());
        existingMoods.add(MoodEntity.builder().name("Mood2").build());
        metadataEntity.setMoods(existingMoods);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setMoods(Set.of("Mood1"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(moodRepository.findByName("Mood1")).thenReturn(Optional.of(MoodEntity.builder().name("Mood1").build()));

        bookMetadataUpdater.setBookMetadata(context);

        // Assert
        assertEquals(1, bookEntity.getMetadata().getMoods().size());
        assertTrue(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood1")));
        assertFalse(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood2")));
    }

    @Test
    void setBookMetadata_withMergeMoodsFalse_andEmptyIncomingSet_shouldClearAllMoods() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<MoodEntity> existingMoods = new HashSet<>();
        existingMoods.add(MoodEntity.builder().name("Mood1").build());
        existingMoods.add(MoodEntity.builder().name("Mood2").build());
        metadataEntity.setMoods(existingMoods);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setMoods(Collections.emptySet());

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertTrue(bookEntity.getMetadata().getMoods().isEmpty(), "All moods should be cleared when incoming set is empty");
    }

    @Test
    void setBookMetadata_withMergeMoodsTrue_shouldMergeMoods() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<MoodEntity> existingMoods = new HashSet<>();
        existingMoods.add(MoodEntity.builder().name("Mood1").build());
        existingMoods.add(MoodEntity.builder().name("Mood2").build());
        metadataEntity.setMoods(existingMoods);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setMoods(Set.of("Mood3"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeMoods(true)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(moodRepository.findByName("Mood3")).thenReturn(Optional.of(MoodEntity.builder().name("Mood3").build()));

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals(3, bookEntity.getMetadata().getMoods().size()); // Mood1, Mood2, Mood3
        assertTrue(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood1")));
        assertTrue(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood2")));
        assertTrue(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood3")));
    }

    @Test
    void setBookMetadata_withLockField_shouldUpdateAndLock() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);

        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTitle("Old Title");
        metadataEntity.setTitleLocked(false);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTitle("New Title");
        newMetadata.setTitleLocked(true);

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals("New Title", bookEntity.getMetadata().getTitle());
        assertTrue(bookEntity.getMetadata().getTitleLocked());
    }

    @Test
    void testUpdateAuthors_WithMergeFalse_ShouldReplaceAuthors() {
        // Setup existing book with "Old Author"
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        Set<AuthorEntity> existingAuthors = new HashSet<>();
        existingAuthors.add(AuthorEntity.builder().name("Old Author").build());
        metadataEntity.setAuthors(existingAuthors);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        // New metadata with "New Author" only
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setAuthors(Set.of("New Author"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        // Update with mergeCategories = false
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeCategories(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(authorRepository.findByName("New Author")).thenReturn(Optional.of(AuthorEntity.builder().name("New Author").build()));

        bookMetadataUpdater.setBookMetadata(context);

        // Verify authors are replaced
        Set<AuthorEntity> authors = bookEntity.getMetadata().getAuthors();
        assertEquals(1, authors.size());
        assertTrue(authors.stream().anyMatch(a -> a.getName().equals("New Author")));
        assertFalse(authors.stream().anyMatch(a -> a.getName().equals("Old Author")));
    }

    @Test
    void testUpdateCategories_WithMergeFalse_ShouldReplaceCategories() {
        // Setup existing book with "Old Category"
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        Set<CategoryEntity> existingCategories = new HashSet<>();
        existingCategories.add(CategoryEntity.builder().name("Old Category").build());
        metadataEntity.setCategories(existingCategories);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        // New metadata with "New Category" only
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New Category"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        // Update with mergeCategories = false
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeCategories(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(categoryRepository.findByName("New Category")).thenReturn(Optional.of(CategoryEntity.builder().name("New Category").build()));

        bookMetadataUpdater.setBookMetadata(context);

        // Verify categories are replaced
        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(1, categories.size());
        assertTrue(categories.stream().anyMatch(c -> c.getName().equals("New Category")));
        assertFalse(categories.stream().anyMatch(c -> c.getName().equals("Old Category")));
    }

    @Test
    void setBookMetadata_withReplaceAllMode_shouldReplaceExistingTitle() {
        // Bug test: When user triggers metadata refresh, the title should be replaced
        // even if the book already has a title (like "V for Vendetta 03 (1988) (c2c) (theProletariat-DCP)")
        
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTitle("V for Vendetta 03 (1988) (c2c) (theProletariat-DCP)"); // Existing file-based title
        metadataEntity.setTitleLocked(false);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTitle("V for Vendetta #3"); // Fetched correct title

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals("V for Vendetta #3", bookEntity.getMetadata().getTitle(),
                "Title should be replaced when using REPLACE_ALL mode");
    }

    @Test
    void setBookMetadata_withReplaceMissingMode_shouldNotReplaceExistingTitle() {
        // This test verifies the old behavior that was causing the bug
        // REPLACE_MISSING mode should NOT replace existing title
        
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTitle("Existing Title"); // Already has a title
        metadataEntity.setTitleLocked(false);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTitle("New Title"); // Fetched title

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_MISSING)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals("Existing Title", bookEntity.getMetadata().getTitle(),
                "Title should NOT be replaced when using REPLACE_MISSING mode and title exists");
    }
}
