package com.adityachandel.booklore.util.builder;

import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.util.Optional;

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

        Assertions.assertThat(book.getAdditionalFiles()
                    .stream()
                    .filter(a -> a.getAdditionalFileType() == AdditionalFileType.ALTERNATIVE_FORMAT)
                    .map(BookAdditionalFileEntity::getFileName)
                    .map(BookFileExtension::fromFileName)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(BookFileExtension::getType))
                .describedAs("Book '%s' should have additional formats: %s", bookTitle, additionalFormatTypes)
                .containsExactlyInAnyOrder(additionalFormatTypes);

        return this;
    }

    public LibraryTestBuilderAssert bookHasSupplementaryFiles(String bookTitle, String ...supplementaryFiles) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        Assertions.assertThat(book.getAdditionalFiles()
                    .stream()
                    .filter(a -> a.getAdditionalFileType() == AdditionalFileType.SUPPLEMENTARY)
                    .map(BookAdditionalFileEntity::getFileName))
                .describedAs("Book '%s' should have supplementary files", bookTitle)
                .containsExactlyInAnyOrder(supplementaryFiles);

        var additionalFiles = actual.getBookAdditionalFiles();
        Assertions.assertThat(additionalFiles)
                .describedAs("Book '%s' should have supplementary files", bookTitle)
                .anyMatch(a -> a.getAdditionalFileType() == AdditionalFileType.SUPPLEMENTARY &&
                        a.getBook().getId().equals(book.getId()) &&
                        a.getFileName().equals(supplementaryFiles[0]));

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoAdditionalFormats(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        Assertions.assertThat(book.getAdditionalFiles()
                    .stream()
                    .filter(a -> a.getAdditionalFileType() == AdditionalFileType.ALTERNATIVE_FORMAT))
                .describedAs("Book '%s' should have no additional formats", bookTitle)
                .isEmpty();

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoSupplementaryFiles(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        Assertions.assertThat(book.getAdditionalFiles())
                .describedAs("Book '%s' should have no supplementary files", bookTitle)
                .noneMatch(a -> a.getAdditionalFileType() == AdditionalFileType.SUPPLEMENTARY);

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoAdditionalFiles(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        Assertions.assertThat(book.getAdditionalFiles())
                .describedAs("Book '%s' should have no additional files", bookTitle)
                .isEmpty();

        return this;
    }
}
