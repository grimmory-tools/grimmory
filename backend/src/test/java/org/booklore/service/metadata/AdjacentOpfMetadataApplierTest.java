package org.booklore.service.metadata;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.extractor.OpfMetadataExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdjacentOpfMetadataApplierTest {

    @Mock
    private OpfMetadataExtractor opfMetadataExtractor;

    @Mock
    private BookCreatorService bookCreatorService;

    @TempDir
    Path tempDir;

    private AdjacentOpfMetadataApplier applier;

    @BeforeEach
    void setUp() {
        applier = new AdjacentOpfMetadataApplier(opfMetadataExtractor, bookCreatorService);

        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> authors = invocation.getArgument(0, List.class);
            BookEntity book = invocation.getArgument(1, BookEntity.class);
            for (String author : authors) {
                book.getMetadata().getAuthors().add(AuthorEntity.builder().name(author).build());
            }
            return null;
        }).when(bookCreatorService).addAuthorsToBook(any(), any(BookEntity.class));

        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Set<String> categories = invocation.getArgument(0, Set.class);
            BookEntity book = invocation.getArgument(1, BookEntity.class);
            for (String category : categories) {
                book.getMetadata().getCategories().add(CategoryEntity.builder().name(category).build());
            }
            return null;
        }).when(bookCreatorService).addCategoriesToBook(any(), any(BookEntity.class));
    }

    @Test
    void applyAdjacentOpfMetadata_prefersSameStemOpfOverMetadataOpf() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("series"));
        Files.writeString(folder.resolve("Book One.epub"), "epub");
        Path sameStem = Files.writeString(folder.resolve("Book One.opf"), "same");
        Files.writeString(folder.resolve("metadata.opf"), "fallback");

        LibraryFile libraryFile = buildLibraryFile(folder, "Book One.epub");
        BookEntity bookEntity = createBookEntity();

        when(opfMetadataExtractor.extractMetadata(sameStem.toFile()))
                .thenReturn(BookMetadata.builder().title("Same Stem Title").build());

        applier.applyAdjacentOpfMetadata(bookEntity, libraryFile);

        verify(opfMetadataExtractor).extractMetadata(sameStem.toFile());
        verify(opfMetadataExtractor, never()).extractMetadata(folder.resolve("metadata.opf").toFile());
        assertThat(bookEntity.getMetadata().getTitle()).isEqualTo("Same Stem Title");
    }

    @Test
    void applyAdjacentOpfMetadata_fallsBackToMetadataOpfAndReplacesCollections() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("series"));
        Files.writeString(folder.resolve("Book One.epub"), "epub");
        Path metadataOpf = Files.writeString(folder.resolve("metadata.opf"), "fallback");

        LibraryFile libraryFile = buildLibraryFile(folder, "Book One.epub");
        BookEntity bookEntity = createBookEntity();
        bookEntity.getMetadata().setTitle("Old Title");
        bookEntity.getMetadata().getAuthors().add(AuthorEntity.builder().name("Old Author").build());
        bookEntity.getMetadata().getCategories().add(CategoryEntity.builder().name("Old Category").build());

        when(opfMetadataExtractor.extractMetadata(metadataOpf.toFile()))
                .thenReturn(BookMetadata.builder()
                        .title("New Title")
                        .authors(List.of("Author A", "Author B"))
                        .categories(new LinkedHashSet<>(Set.of("Fantasy")))
                        .publisher("Kadokawa")
                        .publishedDate(LocalDate.of(2024, 5, 17))
                        .seriesName("High School DxD")
                        .seriesNumber(1.0f)
                        .isbn13("9786161234567")
                        .build());

        applier.applyAdjacentOpfMetadata(bookEntity, libraryFile);

        assertThat(bookEntity.getMetadata().getTitle()).isEqualTo("New Title");
        assertThat(bookEntity.getMetadata().getPublisher()).isEqualTo("Kadokawa");
        assertThat(bookEntity.getMetadata().getPublishedDate()).isEqualTo(LocalDate.of(2024, 5, 17));
        assertThat(bookEntity.getMetadata().getSeriesName()).isEqualTo("High School DxD");
        assertThat(bookEntity.getMetadata().getSeriesNumber()).isEqualTo(1.0f);
        assertThat(bookEntity.getMetadata().getIsbn13()).isEqualTo("9786161234567");
        assertThat(bookEntity.getMetadata().getAuthors()).extracting(AuthorEntity::getName)
                .containsExactly("Author A", "Author B");
        assertThat(bookEntity.getMetadata().getCategories()).extracting(CategoryEntity::getName)
                .containsExactly("Fantasy");
    }

    private LibraryFile buildLibraryFile(Path folder, String fileName) {
        LibraryEntity library = new LibraryEntity();
        library.setOrganizationMode(LibraryOrganizationMode.AUTO_DETECT);

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(folder.toString());

        return LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .fileSubPath("")
                .fileName(fileName)
                .build();
    }

    private BookEntity createBookEntity() {
        BookEntity bookEntity = new BookEntity();
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setAuthors(new ArrayList<>());
        metadata.setCategories(new LinkedHashSet<>());
        metadata.setBook(bookEntity);
        bookEntity.setMetadata(metadata);
        return bookEntity;
    }
}
