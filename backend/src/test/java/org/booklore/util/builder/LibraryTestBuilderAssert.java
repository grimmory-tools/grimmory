package org.booklore.util.builder;

import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.stream.Collectors;

public class LibraryTestBuilderAssert extends AbstractAssert<LibraryTestBuilderAssert, LibraryTestBuilder> {

    protected LibraryTestBuilderAssert(LibraryTestBuilder libraryTestBuilder) {
        super(libraryTestBuilder, LibraryTestBuilderAssert.class);
    }

    public static LibraryTestBuilderAssert assertThat(LibraryTestBuilder actual) {
        return new LibraryTestBuilderAssert(actual);
    }

    public LibraryTestBuilderAssert hasBooks(String ...expectedBookTitles) {
        Assertions.assertThat(actual.getBookEntities())
                .extracting(bookEntity -> bookEntity.getMetadata().getTitle())
                .containsExactlyInAnyOrder(expectedBookTitles);

        return this;
    }

    public LibraryTestBuilderAssert hasNoAdditionalFiles() {
        var additionalFiles = actual.getBookAdditionalFiles();
        Assertions.assertThat(additionalFiles)
                .isEmpty();

        return this;
    }

    public LibraryTestBuilderAssert bookHasAdditionalFormats(String bookTitle, BookFileType ...additionalFormatTypes) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        List<BookFileType> additionalFormatTypesActual = book.getBookFiles()
                .stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(a -> !a.equals(book.getPrimaryBookFile()))
                .map(BookFileEntity::getBookType)
                .filter(a -> a != null)
                .collect(Collectors.toList());

        Assertions.assertThat(additionalFormatTypesActual)
                .describedAs("Book '%s' should have additional formats: %s", bookTitle, additionalFormatTypes)
                .containsExactlyInAnyOrder(additionalFormatTypes);

        return this;
    }

    public LibraryTestBuilderAssert bookHasSupplementaryFiles(String bookTitle, String ...supplementaryFiles) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        Assertions.assertThat(book.getBookFiles()
                    .stream()
                    .filter(a -> !a.isBookFormat())
                    .map(BookFileEntity::getFileName))
                .describedAs("Book '%s' should have supplementary files", bookTitle)
                .containsExactlyInAnyOrder(supplementaryFiles);

        var additionalFiles = actual.getBookAdditionalFiles();
        Assertions.assertThat(additionalFiles)
                .describedAs("Book '%s' should have supplementary files", bookTitle)
                .anyMatch(a -> !a.isBookFormat() &&
                        a.getBook().getId().equals(book.getId()) &&
                        a.getFileName().equals(supplementaryFiles[0]));

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoAdditionalFormats(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        List<BookFileType> additionalFormatTypesActual = book.getBookFiles()
                .stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(a -> !a.equals(book.getPrimaryBookFile()))
                .map(BookFileEntity::getBookType)
                .filter(a -> a != null)
                .collect(Collectors.toList());

        Assertions.assertThat(additionalFormatTypesActual)
                .describedAs("Book '%s' should have no additional formats", bookTitle)
                .isEmpty();

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoSupplementaryFiles(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        Assertions.assertThat(book.getBookFiles())
                .describedAs("Book '%s' should have no supplementary files", bookTitle)
                .noneMatch(a -> !a.isBookFormat());

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoAdditionalFiles(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        Assertions.assertThat(book.getBookFiles())
                .describedAs("Book '%s' should have no additional files", bookTitle)
                .allMatch(BookFileEntity::isBookFormat)
                .containsOnly(book.getPrimaryBookFile());

        return this;
    }
}
