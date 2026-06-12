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

class AdjacentOpfCoverLocatorTest {

    private final AdjacentOpfCoverLocator locator = new AdjacentOpfCoverLocator();

    @TempDir
    Path tempDir;

    @Test
    void prefersMetaCoverManifestItem() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Path opf = folder.resolve("demo.opf");
        Path cover = Files.createFile(folder.resolve("art.jpg"));
        Files.writeString(opf, """
                <package xmlns="http://www.idpf.org/2007/opf">
                  <metadata><meta name="cover" content="cover-id"/></metadata>
                  <manifest>
                    <item id="cover-id" href="art.jpg" media-type="image/jpeg"/>
                  </manifest>
                </package>
                """);

        assertThat(locator.find(opf, libraryFile(folder, "demo.epub"))).contains(cover);
    }

    @Test
    void prefersCoverImagePropertyBeforeIdOrHrefHint() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Path hintedCover = Files.createFile(folder.resolve("cover-old.jpg"));
        Path propertyCover = Files.createFile(folder.resolve("front.jpg"));
        Path opf = folder.resolve("demo.opf");
        Files.writeString(opf, """
                <package xmlns="http://www.idpf.org/2007/opf">
                  <manifest>
                    <item id="cover-old" href="cover-old.jpg" media-type="image/jpeg"/>
                    <item id="front" href="front.jpg" media-type="image/jpeg" properties="cover-image"/>
                  </manifest>
                </package>
                """);

        assertThat(hintedCover).exists();
        assertThat(locator.find(opf, libraryFile(folder, "demo.epub"))).contains(propertyCover);
    }

    @Test
    void rejectsManifestPathEscape() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Files.createFile(tempDir.resolve("outside.jpg"));
        Path opf = folder.resolve("demo.opf");
        Files.writeString(opf, """
                <package xmlns="http://www.idpf.org/2007/opf">
                  <metadata><meta name="cover" content="cover-id"/></metadata>
                  <manifest>
                    <item id="cover-id" href="../outside.jpg" media-type="image/jpeg"/>
                  </manifest>
                </package>
                """);

        assertThat(locator.find(opf, libraryFile(folder, "demo.epub"))).isEmpty();
    }

    @Test
    void fallsBackToBookStemCover() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Path opf = Files.createFile(folder.resolve("demo.opf"));
        Path cover = Files.createFile(folder.resolve("demo.png"));

        assertThat(locator.find(opf, libraryFile(folder, "demo.epub"))).contains(cover);
    }

    @Test
    void readsManifestCoverWhenRdfNamespaceIsMissing() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("books"));
        Path opf = folder.resolve("demo.opf");
        Path cover = Files.createFile(folder.resolve("front.jpg"));
        Files.writeString(opf, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdf:RDF xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <rdf:Description>
                    <meta name="cover" content="front"/>
                    <manifest>
                      <item id="front" href="front.jpg" media-type="image/jpeg" properties="cover-image"/>
                    </manifest>
                  </rdf:Description>
                </rdf:RDF>
                """);

        assertThat(locator.find(opf, libraryFile(folder, "demo.epub"))).contains(cover);
    }

    private LibraryFile libraryFile(Path folder, String fileName) {
        var libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());

        var library = new LibraryEntity();
        library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FOLDER);

        Path fullPath = folder.resolve(fileName);
        Path relative = tempDir.relativize(fullPath);
        return LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .fileSubPath(relative.getParent() != null ? relative.getParent().toString() : "")
                .fileName(relative.getFileName().toString())
                .folderBased(false)
                .build();
    }
}
