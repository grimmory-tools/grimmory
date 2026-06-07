package org.booklore.opf;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdjacentOpfLocatorTest {

    private final AdjacentOpfLocator locator = new AdjacentOpfLocator();

    @TempDir
    Path tempDir;

    @Test
    void findsBookStemOpfFirst() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Path expected = Files.createFile(folder.resolve("demo.opf"));
        Files.createFile(folder.resolve("metadata.opf"));

        assertThat(locator.find(libraryFile(folder, "demo.epub", false))).contains(expected);
    }

    @Test
    void findsFolderNameOpf() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("Novel"));
        Path expected = Files.createFile(folder.resolve("Novel.opf"));

        assertThat(locator.find(libraryFile(folder, "book.epub", false))).contains(expected);
    }

    @Test
    void findsMetadataOpf() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Path expected = Files.createFile(folder.resolve("metadata.opf"));

        assertThat(locator.find(libraryFile(folder, "demo.epub", false))).contains(expected);
    }

    @Test
    void skipsAmbiguousSingleOpfFallbackInMultiBookFolder() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Files.createFile(folder.resolve("one.epub"));
        Files.createFile(folder.resolve("two.epub"));
        Files.createFile(folder.resolve("sidecar.opf"));

        assertThat(locator.find(libraryFile(folder, "one.epub", false))).isEmpty();
    }

    @Test
    void doesNotScanRecursively() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Files.createDirectories(folder.resolve("nested"));
        Files.createFile(folder.resolve("nested").resolve("demo.opf"));

        assertThat(locator.find(libraryFile(folder, "demo.epub", false))).isEmpty();
    }

    @Test
    void rejectsPathEscape() {
        Path folder = tempDir.resolve("books");
        Path escaped = folder.resolve("..").resolve("metadata.opf").normalize();

        assertThat(locator.isInside(folder, escaped)).isFalse();
    }

    private LibraryFile libraryFile(Path folder, String fileName, boolean folderBased) {
        var libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());

        var library = new LibraryEntity();
        library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FOLDER);

        Path fullPath = folderBased ? folder : folder.resolve(fileName);
        Path relative = tempDir.relativize(fullPath);
        return LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .fileSubPath(relative.getParent() != null ? relative.getParent().toString() : "")
                .fileName(relative.getFileName().toString())
                .folderBased(folderBased)
                .build();
    }
}
