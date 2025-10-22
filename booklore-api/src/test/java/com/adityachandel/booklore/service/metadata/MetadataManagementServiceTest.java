package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.MergeMetadataType;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataManagementServiceTest {

    @Mock
    AuthorRepository authorRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    MoodRepository moodRepository;
    @Mock
    TagRepository tagRepository;
    @Mock
    BookMetadataRepository bookMetadataRepository;

    @Mock
    AppSettingService appSettingService;
    @Mock
    MetadataWriterFactory metadataWriterFactory;

    @InjectMocks
    MetadataManagementService service;

    @Captor
    ArgumentCaptor<List<BookMetadataEntity>> bookListCaptor;

    @BeforeEach
    void setUp() {
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPersistenceSettings(new MetadataPersistenceSettings());
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    @Test
    void mergeAuthors_createsTargetAndMovesBooksAndDeletesOldAuthor() {
        String targetName = "New Author";
        String oldName = "Old Author";

        AuthorEntity oldAuthor = new AuthorEntity();
        oldAuthor.setName(oldName);

        when(authorRepository.findByName(targetName)).thenReturn(Optional.empty());
        when(authorRepository.save(any(AuthorEntity.class))).thenAnswer(invocation -> {
            AuthorEntity a = invocation.getArgument(0);
            a.setName(a.getName());
            return a;
        });

        when(authorRepository.findByName(oldName)).thenReturn(Optional.of(oldAuthor));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<AuthorEntity> authorsSet = new HashSet<>();
        authorsSet.add(oldAuthor);
        when(metadata.getAuthors()).thenReturn(authorsSet);

        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.authors, List.of(targetName), List.of(oldName));

        assertThat(authorsSet).doesNotContain(oldAuthor);
        assertThat(authorsSet).extracting(AuthorEntity::getName).contains(targetName);

        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
        List<BookMetadataEntity> saved = bookListCaptor.getValue();
        assertThat(saved).containsExactly(metadata);

        verify(authorRepository).delete(oldAuthor);
    }

    @Test
    void deleteAuthors_removesFromBooksAndDeletes() {
        String name = "Author To Delete";
        AuthorEntity author = new AuthorEntity();
        author.setName(name);

        when(authorRepository.findByName(name)).thenReturn(Optional.of(author));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<AuthorEntity> authorsSet = new HashSet<>();
        authorsSet.add(author);
        when(metadata.getAuthors()).thenReturn(authorsSet);

        when(bookMetadataRepository.findAllByAuthorsContaining(author)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.authors, List.of(name));

        assertThat(authorsSet).doesNotContain(author);
        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
        verify(authorRepository).delete(author);
    }

    @Test
    void mergeCategories_movesAndDeletesOldCategory() {
        String targetName = "New Category";
        String oldName = "Old Category";

        CategoryEntity oldCategory = new CategoryEntity();
        oldCategory.setName(oldName);

        when(categoryRepository.findByNameIgnoreCase(targetName)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        when(categoryRepository.findByNameIgnoreCase(oldName)).thenReturn(Optional.of(oldCategory));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<CategoryEntity> categories = new HashSet<>();
        categories.add(oldCategory);
        when(metadata.getCategories()).thenReturn(categories);

        when(bookMetadataRepository.findAllByCategoriesContaining(oldCategory)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.categories, List.of(targetName), List.of(oldName));

        assertThat(categories).doesNotContain(oldCategory);
        assertThat(categories).extracting(CategoryEntity::getName).contains(targetName);
        verify(categoryRepository).delete(oldCategory);
        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
    }

    @Test
    void deleteCategories_removesAndDeletes() {
        String name = "CategoryToDelete";
        CategoryEntity cat = new CategoryEntity();
        cat.setName(name);

        when(categoryRepository.findByNameIgnoreCase(name)).thenReturn(Optional.of(cat));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<CategoryEntity> cats = new HashSet<>();
        cats.add(cat);
        when(metadata.getCategories()).thenReturn(cats);

        when(bookMetadataRepository.findAllByCategoriesContaining(cat)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.categories, List.of(name));

        assertThat(cats).doesNotContain(cat);
        verify(categoryRepository).delete(cat);
        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
    }

    @Test
    void mergeTags_movesAndDeletesOldTag() {
        String targetName = "New Tag";
        String oldName = "Old Tag";

        TagEntity oldTag = new TagEntity();
        oldTag.setName(oldName);

        when(tagRepository.findByNameIgnoreCase(targetName)).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(tagRepository.findByNameIgnoreCase(oldName)).thenReturn(Optional.of(oldTag));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<TagEntity> tags = new HashSet<>();
        tags.add(oldTag);
        when(metadata.getTags()).thenReturn(tags);

        when(bookMetadataRepository.findAllByTagsContaining(oldTag)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.tags, List.of(targetName), List.of(oldName));

        assertThat(tags).doesNotContain(oldTag);
        assertThat(tags).extracting(TagEntity::getName).contains(targetName);
        verify(tagRepository).delete(oldTag);
        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
    }

    @Test
    void deleteTags_removesAndDeletes() {
        String name = "TagToDelete";
        TagEntity tag = new TagEntity();
        tag.setName(name);

        when(tagRepository.findByNameIgnoreCase(name)).thenReturn(Optional.of(tag));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<TagEntity> tags = new HashSet<>();
        tags.add(tag);
        when(metadata.getTags()).thenReturn(tags);

        when(bookMetadataRepository.findAllByTagsContaining(tag)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.tags, List.of(name));

        assertThat(tags).doesNotContain(tag);
        verify(tagRepository).delete(tag);
        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
    }

    @Test
    void mergeMoods_movesAndDeletesOldMood() {
        String targetName = "New Mood";
        String oldName = "Old Mood";

        MoodEntity oldMood = new MoodEntity();
        oldMood.setName(oldName);

        when(moodRepository.findByNameIgnoreCase(targetName)).thenReturn(Optional.empty());
        when(moodRepository.save(any(MoodEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(moodRepository.findByNameIgnoreCase(oldName)).thenReturn(Optional.of(oldMood));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<MoodEntity> moods = new HashSet<>();
        moods.add(oldMood);
        when(metadata.getMoods()).thenReturn(moods);

        when(bookMetadataRepository.findAllByMoodsContaining(oldMood)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.moods, List.of(targetName), List.of(oldName));

        assertThat(moods).doesNotContain(oldMood);
        assertThat(moods).extracting(MoodEntity::getName).contains(targetName);
        verify(moodRepository).delete(oldMood);
        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
    }

    @Test
    void deleteMoods_removesAndDeletes() {
        String name = "MoodToDelete";
        MoodEntity mood = new MoodEntity();
        mood.setName(name);

        when(moodRepository.findByNameIgnoreCase(name)).thenReturn(Optional.of(mood));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<MoodEntity> moods = new HashSet<>();
        moods.add(mood);
        when(metadata.getMoods()).thenReturn(moods);

        when(bookMetadataRepository.findAllByMoodsContaining(mood)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.moods, List.of(name));

        assertThat(moods).doesNotContain(mood);
        verify(moodRepository).delete(mood);
        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
    }

    @Test
    void deleteSeries_clearsSeriesFields() {
        String seriesName = "Some Series";

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        when(bookMetadataRepository.findAllBySeriesNameIgnoreCase(seriesName)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.series, List.of(seriesName));

        verify(metadata).setSeriesName(null);
        verify(metadata).setSeriesNumber(null);
        verify(metadata).setSeriesTotal(null);
        verify(bookMetadataRepository).saveAll(anyList());
    }

    @Test
    void mergeSeries_withMultipleTargets_throws() {
        List<String> targets = List.of("A", "B");
        List<String> valuesToMerge = List.of("Old");

        assertThrows(IllegalArgumentException.class,
                () -> service.consolidateMetadata(MergeMetadataType.series, targets, valuesToMerge));
    }

    @Test
    void mergePublishers_withMultipleTargets_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.consolidateMetadata(MergeMetadataType.publishers, List.of("P1", "P2"), List.of("Old")));
    }

    @Test
    void mergeLanguages_withMultipleTargets_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.consolidateMetadata(MergeMetadataType.languages, List.of("L1", "L2"), List.of("Old")));
    }

    @Test
    void mergeAuthors_targetExists_usesExistingTargetAndDeletesOld() {
        String targetName = "Existing Author";
        String oldName = "Old Author";

        AuthorEntity existingAuthor = new AuthorEntity();
        existingAuthor.setName(targetName);

        AuthorEntity oldAuthor = new AuthorEntity();
        oldAuthor.setName(oldName);

        when(authorRepository.findByName(targetName)).thenReturn(Optional.of(existingAuthor));
        when(authorRepository.findByName(oldName)).thenReturn(Optional.of(oldAuthor));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<AuthorEntity> authorsSet = new HashSet<>();
        authorsSet.add(oldAuthor);
        when(metadata.getAuthors()).thenReturn(authorsSet);

        when(bookMetadataRepository.findAllByAuthorsContaining(oldAuthor)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.authors, List.of(targetName), List.of(oldName));

        assertThat(authorsSet).doesNotContain(oldAuthor);
        assertThat(authorsSet).contains(existingAuthor);

        verify(authorRepository, never()).save(any(AuthorEntity.class));
        verify(authorRepository).delete(oldAuthor);
        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
    }

    @Test
    void mergeTags_mergesMultipleOldTagsIntoSingleTarget() {
        String targetName = "UnifiedTag";
        String old1 = "OldTag1";
        String old2 = "OldTag2";

        TagEntity oldTag1 = new TagEntity();
        oldTag1.setName(old1);
        TagEntity oldTag2 = new TagEntity();
        oldTag2.setName(old2);

        when(tagRepository.findByNameIgnoreCase(targetName)).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(i -> {
            TagEntity t = i.getArgument(0);
            return t;
        });
        when(tagRepository.findByNameIgnoreCase(old1)).thenReturn(Optional.of(oldTag1));
        when(tagRepository.findByNameIgnoreCase(old2)).thenReturn(Optional.of(oldTag2));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<TagEntity> tags = new HashSet<>();
        tags.add(oldTag1);
        tags.add(oldTag2);
        when(metadata.getTags()).thenReturn(tags);

        when(bookMetadataRepository.findAllByTagsContaining(oldTag1)).thenReturn(List.of(metadata));
        when(bookMetadataRepository.findAllByTagsContaining(oldTag2)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.tags, List.of(targetName), List.of(old1, old2));

        assertThat(tags).doesNotContain(oldTag1, oldTag2);
        assertThat(tags).extracting(TagEntity::getName).contains(targetName);
        verify(tagRepository).delete(oldTag1);
        verify(tagRepository).delete(oldTag2);
        verify(bookMetadataRepository, times(2)).saveAll(anyList());
    }

    @Test
    void mergeAuthors_ignoresNonExistentOldAuthor() {
        String targetName = "TargetAuthor";
        String existingOld = "ExistingOld";
        String missingOld = "MissingOld";

        AuthorEntity target = new AuthorEntity();
        target.setName(targetName);
        AuthorEntity old = new AuthorEntity();
        old.setName(existingOld);

        when(authorRepository.findByName(targetName)).thenReturn(Optional.of(target));
        when(authorRepository.findByName(existingOld)).thenReturn(Optional.of(old));
        when(authorRepository.findByName(missingOld)).thenReturn(Optional.empty());

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<AuthorEntity> authors = new HashSet<>();
        authors.add(old);
        when(metadata.getAuthors()).thenReturn(authors);

        when(bookMetadataRepository.findAllByAuthorsContaining(old)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.authors, List.of(targetName), List.of(existingOld, missingOld));

        assertThat(authors).doesNotContain(old);
        assertThat(authors).contains(target);
        verify(authorRepository).delete(old);
        verify(authorRepository, never()).delete(argThat(a -> missingOld.equals(a.getName())));
        verify(bookMetadataRepository).saveAll(anyList());
    }

    @Test
    void mergeCategories_doesNotDuplicateExistingTarget() {
        String targetName = "CatTarget";
        String oldName = "OldCat";

        CategoryEntity target = new CategoryEntity();
        target.setName(targetName);
        CategoryEntity old = new CategoryEntity();
        old.setName(oldName);

        when(categoryRepository.findByNameIgnoreCase(targetName)).thenReturn(Optional.of(target));
        when(categoryRepository.findByNameIgnoreCase(oldName)).thenReturn(Optional.of(old));

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<CategoryEntity> cats = new HashSet<>();
        cats.add(old);
        cats.add(target);
        when(metadata.getCategories()).thenReturn(cats);

        when(bookMetadataRepository.findAllByCategoriesContaining(old)).thenReturn(List.of(metadata));

        service.consolidateMetadata(MergeMetadataType.categories, List.of(targetName), List.of(oldName));

        assertThat(cats).doesNotContain(old);
        assertThat(cats).contains(target);
        verify(categoryRepository).delete(old);
        verify(bookMetadataRepository).saveAll(anyList());
    }

    @Test
    void deleteSeries_noBooksDoesNothing() {
        when(bookMetadataRepository.findAllBySeriesNameIgnoreCase("NoSeries")).thenReturn(List.of());
        service.deleteMetadata(MergeMetadataType.series, List.of("NoSeries"));
        verify(bookMetadataRepository, never()).saveAll(anyList());
    }

    @Test
    void deletePublishers_noBooksDoesNothing() {
        when(bookMetadataRepository.findAllByPublisherIgnoreCase("NoPublisher")).thenReturn(List.of());
        service.deleteMetadata(MergeMetadataType.publishers, List.of("NoPublisher"));
        verify(bookMetadataRepository, never()).saveAll(anyList());
    }

    @Test
    void mergeTags_noBooks_deletesOldTag() {
        String targetName = "TargetTag";
        String oldName = "OldTag";

        TagEntity target = new TagEntity();
        target.setName(targetName);
        TagEntity old = new TagEntity();
        old.setName(oldName);

        when(tagRepository.findByNameIgnoreCase(targetName)).thenReturn(Optional.of(target));
        when(tagRepository.findByNameIgnoreCase(oldName)).thenReturn(Optional.of(old));

        when(bookMetadataRepository.findAllByTagsContaining(old)).thenReturn(List.of());

        service.consolidateMetadata(MergeMetadataType.tags, List.of(targetName), List.of(oldName));

        verify(bookMetadataRepository).saveAll(bookListCaptor.capture());
        List<BookMetadataEntity> saved = bookListCaptor.getValue();
        assertThat(saved).isEmpty();
        verify(tagRepository).delete(old);
    }

    @Test
    void deleteAuthors_noMatchingAuthor_noOps() {
        when(authorRepository.findByName("NoAuthor")).thenReturn(Optional.empty());

        service.deleteMetadata(MergeMetadataType.authors, List.of("NoAuthor"));

        verify(bookMetadataRepository, never()).saveAll(anyList());
        verify(authorRepository, never()).delete(any());
    }

    @Test
    void deleteTags_partialMissing_ignoresMissing() {
        String present = "PresentTag";
        String missing = "MissingTag";

        TagEntity presentTag = new TagEntity();
        presentTag.setName(present);

        when(tagRepository.findByNameIgnoreCase(present)).thenReturn(Optional.of(presentTag));
        when(tagRepository.findByNameIgnoreCase(missing)).thenReturn(Optional.empty());

        BookMetadataEntity metadata = mock(BookMetadataEntity.class);
        Set<TagEntity> tags = new HashSet<>();
        tags.add(presentTag);
        when(metadata.getTags()).thenReturn(tags);

        when(bookMetadataRepository.findAllByTagsContaining(presentTag)).thenReturn(List.of(metadata));

        service.deleteMetadata(MergeMetadataType.tags, List.of(present, missing));

        assertThat(tags).doesNotContain(presentTag);
        verify(tagRepository).delete(presentTag);
        verify(tagRepository, never()).delete(argThat(t -> missing.equals(t.getName())));
        verify(bookMetadataRepository).saveAll(anyList());
    }
}
