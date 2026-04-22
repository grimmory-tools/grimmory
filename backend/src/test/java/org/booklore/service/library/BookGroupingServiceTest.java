package org.booklore.service.library;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BookGroupingServiceTest {

    @Mock
    BookRepository bookRepository;

    @InjectMocks
    BookGroupingService bookGroupingService;

    private LibraryPathEntity pathEntity(Long id, String path) {
        var pe = new LibraryPathEntity();
        pe.setId(id);
        pe.setPath(path);
        return pe;
    }

    private LibraryEntity library(LibraryOrganizationMode mode, LibraryPathEntity... paths) {
        return LibraryEntity.builder()
                .name("Test Library")
                .organizationMode(mode)
                .libraryPaths(List.of(paths))
                .build();
    }

    private LibraryFile file(LibraryPathEntity path, String subPath, String name, BookFileType type) {
        return LibraryFile.builder()
                .libraryPathEntity(path)
                .fileSubPath(subPath)
                .fileName(name)
                .bookFileType(type)
                .build();
    }

    private LibraryFile file(LibraryPathEntity path, String subPath, String name, BookFileType type, boolean folderBased) {
        return LibraryFile.builder()
                .libraryPathEntity(path)
                .fileSubPath(subPath)
                .fileName(name)
                .bookFileType(type)
                .folderBased(folderBased)
                .build();
    }

    private LibraryFile ebook(LibraryPathEntity path, String subPath, String name) {
        return file(path, subPath, name, BookFileType.EPUB);
    }

    private LibraryFile audiobook(LibraryPathEntity path, String subPath, String name) {
        return file(path, subPath, name, BookFileType.AUDIOBOOK);
    }

    private LibraryFile folderAudiobook(LibraryPathEntity path, String subPath, String folderName) {
        return file(path, subPath, folderName, BookFileType.AUDIOBOOK, true);
    }

    private int groupCount(Map<String, List<LibraryFile>> groups) {
        return groups.size();
    }

    private int totalFiles(Map<String, List<LibraryFile>> groups) {
        return groups.values().stream().mapToInt(List::size).sum();
    }

    private int folderBasedCount(Map<String, List<LibraryFile>> groups) {
        return (int) groups.values().stream()
                .flatMap(Collection::stream)
                .filter(LibraryFile::isFolderBased)
                .count();
    }

    @Test
    void structure1_bookPerFile_flatRootFiles() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "", "Salem's Lot (Package May Vary) - Stephen Kingg, Aditya.epub"),
                ebook(path, "", "Good Spirits (B.K. Borison) (z-library.sk, 1lib.sk, z-lib.sk).epub"),
                ebook(path, "", "Hansel and Gretel - Stephen King (2025).epub"),
                audiobook(path, "", "Hansel and Gretel (2025).m4b"),
                ebook(path, "", "History of Britain and Ireland The Definitive Visual Guide (DORLING KINDERSLEY.) (Z-Library).epub"),
                ebook(path, "", "The Strength of the Few (Hierarchy, Book Two) (James Islington) (z-library.sk, 1lib.sk, z-lib.sk).epub"),
                ebook(path, "", "Violet Thistlewaite Is Not a Villain Anymore (Emily Krempholtz) (z-library.sk, 1lib.sk, z-lib.sk).epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(7);
        assertThat(totalFiles(groups)).isEqualTo(7);
        assertThat(folderBasedCount(groups)).isZero();
    }

    @Test
    void structure1_bookPerFolder_flatRootFiles() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "", "Salem's Lot (Package May Vary) - Stephen Kingg, Aditya.epub"),
                ebook(path, "", "Good Spirits (B.K. Borison) (z-library.sk, 1lib.sk, z-lib.sk).epub"),
                ebook(path, "", "Hansel and Gretel - Stephen King (2025).epub"),
                audiobook(path, "", "Hansel and Gretel (2025).m4b"),
                ebook(path, "", "History of Britain and Ireland The Definitive Visual Guide (DORLING KINDERSLEY.) (Z-Library).epub"),
                ebook(path, "", "The Strength of the Few (Hierarchy, Book Two) (James Islington) (z-library.sk, 1lib.sk, z-lib.sk).epub"),
                ebook(path, "", "Violet Thistlewaite Is Not a Villain Anymore (Emily Krempholtz) (z-library.sk, 1lib.sk, z-lib.sk).epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(7);
        assertThat(totalFiles(groups)).isEqualTo(7);
    }

    @Test
    void structure2_bookPerFile_authorTitleFolders() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Andy Weir/Project Hail Mary", "Project Hail Mary - Andy Weir (2022).pdf"),
                ebook(path, "Andy Weir/The Martian", "The Martian - Andy Weir (2020).epub"),
                audiobook(path, "Andy Weir/The Martian", "The Martian - Andy Weir (2020).mp3"),
                ebook(path, "Christopher Buehlman/The Lesser Dead", "The Lesser Dead - Christopher Buehlman (2014).epub"),
                ebook(path, "Christopher Buehlman/Those Across the River", "Those Across the River - Christopher Buehlman (2011).epub"),
                ebook(path, "Cormac McCarthy/The Road", "The Road - Cormac McCarthy (2006).epub"),
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).azw3"),
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).epub"),
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).mobi"),
                ebook(path, "Neil Gaiman/American Gods", "American Gods - Neil Gaiman (2011).epub"),
                ebook(path, "Neil Gaiman/American Gods", "American Gods - Neil Gaiman (2011).mobi"),
                ebook(path, "James Islington/The Will of the Many", "The Will of the Many (Hierarchy) - James Islington (2025).epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(12);
        assertThat(totalFiles(groups)).isEqualTo(12);
        assertThat(folderBasedCount(groups)).isZero();
    }

    @Test
    void structure2_bookPerFolder_authorTitleFolders() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Andy Weir/Project Hail Mary", "Project Hail Mary - Andy Weir (2022).pdf"),
                ebook(path, "Andy Weir/The Martian", "The Martian - Andy Weir (2020).epub"),
                audiobook(path, "Andy Weir/The Martian", "The Martian - Andy Weir (2020).mp3"),
                ebook(path, "Christopher Buehlman/The Lesser Dead", "The Lesser Dead - Christopher Buehlman (2014).epub"),
                ebook(path, "Christopher Buehlman/Those Across the River", "Those Across the River - Christopher Buehlman (2011).epub"),
                ebook(path, "Cormac McCarthy/The Road", "The Road - Cormac McCarthy (2006).epub"),
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).azw3"),
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).epub"),
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).mobi"),
                ebook(path, "Neil Gaiman/American Gods", "American Gods - Neil Gaiman (2011).epub"),
                ebook(path, "Neil Gaiman/American Gods", "American Gods - Neil Gaiman (2011).mobi"),
                ebook(path, "James Islington/The Will of the Many", "The Will of the Many (Hierarchy) - James Islington (2025).epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(8);
        assertThat(totalFiles(groups)).isEqualTo(12);
    }

    @Test
    void structure2_bookPerFolder_twelveMonthsGroupedAsSingleBook() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).azw3"),
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).epub"),
                ebook(path, "Jim Butcher/Twelve Months", "Twelve Months - Jim Butcher (2026).mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(1);
        assertThat(totalFiles(groups)).isEqualTo(3);
    }

    @Test
    void structure3_bookPerFile_folderBasedAudiobooks() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "1 HARRY POTTER AND THE PHILOSOPHER'S STONE"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "2 HARRY POTTER AND THE CHAMBER OF SECRETS"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "3 HARRY POTTER AND THE PRISONER OF AZKABAN")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(3);
        assertThat(folderBasedCount(groups)).isEqualTo(3);
    }

    @Test
    void structure3_bookPerFolder_folderBasedAudiobooksGroupedUnderParent() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "1 HARRY POTTER AND THE PHILOSOPHER'S STONE"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "2 HARRY POTTER AND THE CHAMBER OF SECRETS"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "3 HARRY POTTER AND THE PRISONER OF AZKABAN")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        // Folder-based audiobooks reconstruct effectiveSubPath = subPath/fileName, giving each a
        // unique folder path. With no ebook ancestor to absorb into, each stays separate.
        assertThat(groupCount(groups)).isEqualTo(3);
        assertThat(totalFiles(groups)).isEqualTo(3);
        assertThat(folderBasedCount(groups)).isEqualTo(3);
    }

    @Test
    void structure4_bookPerFile_mixedMedia() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Suzanne Collins/Catching Fire", "Catching Fire - Suzanne Collins.azw3"),
                ebook(path, "Suzanne Collins/Catching Fire", "Catching Fire - Suzanne Collins.epub"),
                folderAudiobook(path, "Suzanne Collins/Catching Fire/audiobook", "audiobook"),
                ebook(path, "Suzanne Collins/The Hunger Games", "The Hunger Games - Suzanne Collins (2010).azw3"),
                ebook(path, "Suzanne Collins/The Hunger Games", "The Hunger Games - Suzanne Collins (2010).epub"),
                ebook(path, "Suzanne Collins/The Hunger Games", "The Hunger Games - Suzanne Collins (2010).mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(6);
        assertThat(totalFiles(groups)).isEqualTo(6);
        assertThat(folderBasedCount(groups)).isEqualTo(1);
    }

    @Test
    void structure4_bookPerFolder_audiobookAbsorbedIntoEbookFolder() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Suzanne Collins/Catching Fire", "Catching Fire - Suzanne Collins.azw3"),
                ebook(path, "Suzanne Collins/Catching Fire", "Catching Fire - Suzanne Collins.epub"),
                folderAudiobook(path, "Suzanne Collins/Catching Fire/audiobook", "audiobook"),
                ebook(path, "Suzanne Collins/The Hunger Games", "The Hunger Games - Suzanne Collins (2010).azw3"),
                ebook(path, "Suzanne Collins/The Hunger Games", "The Hunger Games - Suzanne Collins (2010).epub"),
                ebook(path, "Suzanne Collins/The Hunger Games", "The Hunger Games - Suzanne Collins (2010).mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(2);
        assertThat(totalFiles(groups)).isEqualTo(6);
        assertThat(folderBasedCount(groups)).isEqualTo(1);

        var catchingFireGroup = groups.values().stream()
                .filter(g -> g.stream().anyMatch(f -> f.getFileName().contains("Catching Fire")))
                .findFirst().orElseThrow();
        assertThat(catchingFireGroup).hasSize(3);
    }

    @Test
    void structure5_bookPerFile_formatSubfolders() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - EPUB", "Brandon Sanderson - Mistborn 01 - The Final Empire.epub"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - EPUB", "Brandon Sanderson - Mistborn 02 - The Well of Ascension.epub"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - EPUB", "Brandon Sanderson - Mistborn 03 - The Hero of Ages.epub"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - MOBI", "Brandon Sanderson - Mistborn 01 - The Final Empire.mobi"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - MOBI", "Brandon Sanderson - Mistborn 02 - The Well of Ascension.mobi"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - MOBI", "Brandon Sanderson - Mistborn 03 - The Hero of Ages.mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(6);
        assertThat(totalFiles(groups)).isEqualTo(6);
    }

    @Test
    void structure5_bookPerFolder_formatSubfolders() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - EPUB", "Brandon Sanderson - Mistborn 01 - The Final Empire.epub"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - EPUB", "Brandon Sanderson - Mistborn 02 - The Well of Ascension.epub"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - EPUB", "Brandon Sanderson - Mistborn 03 - The Hero of Ages.epub"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - MOBI", "Brandon Sanderson - Mistborn 01 - The Final Empire.mobi"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - MOBI", "Brandon Sanderson - Mistborn 02 - The Well of Ascension.mobi"),
                ebook(path, "Brandon Sanderson - Mistborn Trilogy/Brandon Sanderson - Mistborn - MOBI", "Brandon Sanderson - Mistborn 03 - The Hero of Ages.mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(2);
        assertThat(totalFiles(groups)).isEqualTo(6);
    }

    @Test
    void structure6_bookPerFile_singleFileAudiobooksWithEbooks() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "J. M. Barrie/Peter Pan", "Peter Pan - J. M. Barrie (2013).epub"),
                audiobook(path, "J. M. Barrie/Peter Pan", "Peter Pan - J. M. Barrie (2013).m4b"),
                ebook(path, "J. M. Barrie/Peter Pan", "Peter Pan - J. M. Barrie (2013).mobi"),
                ebook(path, "Jonathan Swift/Gulliver's Travels", "Gulliver's Travels - Jonathan Swift (2013).epub"),
                audiobook(path, "Jonathan Swift/Gulliver's Travels", "Gulliver's Travels - Jonathan Swift (2013).m4b"),
                ebook(path, "Jonathan Swift/Gulliver's Travels", "Gulliver's Travels - Jonathan Swift (2013).mobi"),
                ebook(path, "", "Hansel and Gretel - Stephen King (2025).epub"),
                audiobook(path, "", "Hansel and Gretel (2025).m4b")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(8);
        assertThat(totalFiles(groups)).isEqualTo(8);
    }

    @Test
    void structure6_bookPerFolder_singleFileAudiobooksGroupedWithEbooks() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "J. M. Barrie/Peter Pan", "Peter Pan - J. M. Barrie (2013).epub"),
                audiobook(path, "J. M. Barrie/Peter Pan", "Peter Pan - J. M. Barrie (2013).m4b"),
                ebook(path, "J. M. Barrie/Peter Pan", "Peter Pan - J. M. Barrie (2013).mobi"),
                ebook(path, "Jonathan Swift/Gulliver's Travels", "Gulliver's Travels - Jonathan Swift (2013).epub"),
                audiobook(path, "Jonathan Swift/Gulliver's Travels", "Gulliver's Travels - Jonathan Swift (2013).m4b"),
                ebook(path, "Jonathan Swift/Gulliver's Travels", "Gulliver's Travels - Jonathan Swift (2013).mobi"),
                ebook(path, "", "Hansel and Gretel - Stephen King (2025).epub"),
                audiobook(path, "", "Hansel and Gretel (2025).m4b")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(4);
        assertThat(totalFiles(groups)).isEqualTo(8);
    }

    @Test
    void structure7_bookPerFile_titleFolders() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Oathbringer_ The Stormlight Archive Book Three", "Oathbringer_ The Stormlight Archive Book T - Brandon Sanderson.azw3"),
                ebook(path, "Oathbringer_ The Stormlight Archive Book Three", "Oathbringer_ The Stormlight Archive Book T - Brandon Sanderson.epub"),
                ebook(path, "Oathbringer_ The Stormlight Archive Book Three", "Oathbringer_ The Stormlight Archive Book T - Brandon Sanderson.mobi"),
                ebook(path, "Piranesi by Susanna Clarke", "Piranesi - Susanna Clarke.epub"),
                ebook(path, "Piranesi by Susanna Clarke", "Piranesi - Susanna Clarke.mobi"),
                ebook(path, "Rhythm of War (Stormlight Archive Book 4) - Brandon Sanderson", "Rhythm of War (The Stormlight Archive Book 4) - Brandon Sanderson.azw3"),
                ebook(path, "Rhythm of War (Stormlight Archive Book 4) - Brandon Sanderson", "Rhythm of War (The Stormlight Archive Book 4) - Brandon Sanderson.epub"),
                ebook(path, "Rhythm of War (Stormlight Archive Book 4) - Brandon Sanderson", "Rhythm of War (The Stormlight Archive Book 4) - Brandon Sanderson.mobi"),
                ebook(path, "The Martian_ A Novel (283)", "The Martian_ A Novel - Andy Weir.azw3"),
                ebook(path, "The Martian_ A Novel (283)", "The Martian_ A Novel - Andy Weir.epub"),
                ebook(path, "The Martian_ A Novel (283)", "The Martian_ A Novel - Andy Weir.mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(11);
        assertThat(totalFiles(groups)).isEqualTo(11);
    }

    @Test
    void structure7_bookPerFolder_titleFolders() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Oathbringer_ The Stormlight Archive Book Three", "Oathbringer_ The Stormlight Archive Book T - Brandon Sanderson.azw3"),
                ebook(path, "Oathbringer_ The Stormlight Archive Book Three", "Oathbringer_ The Stormlight Archive Book T - Brandon Sanderson.epub"),
                ebook(path, "Oathbringer_ The Stormlight Archive Book Three", "Oathbringer_ The Stormlight Archive Book T - Brandon Sanderson.mobi"),
                ebook(path, "Piranesi by Susanna Clarke", "Piranesi - Susanna Clarke.epub"),
                ebook(path, "Piranesi by Susanna Clarke", "Piranesi - Susanna Clarke.mobi"),
                ebook(path, "Rhythm of War (Stormlight Archive Book 4) - Brandon Sanderson", "Rhythm of War (The Stormlight Archive Book 4) - Brandon Sanderson.azw3"),
                ebook(path, "Rhythm of War (Stormlight Archive Book 4) - Brandon Sanderson", "Rhythm of War (The Stormlight Archive Book 4) - Brandon Sanderson.epub"),
                ebook(path, "Rhythm of War (Stormlight Archive Book 4) - Brandon Sanderson", "Rhythm of War (The Stormlight Archive Book 4) - Brandon Sanderson.mobi"),
                ebook(path, "The Martian_ A Novel (283)", "The Martian_ A Novel - Andy Weir.azw3"),
                ebook(path, "The Martian_ A Novel (283)", "The Martian_ A Novel - Andy Weir.epub"),
                ebook(path, "The Martian_ A Novel (283)", "The Martian_ A Novel - Andy Weir.mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(4);
        assertThat(totalFiles(groups)).isEqualTo(11);
    }

    @Test
    void structure8_bookPerFile_deepNesting() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/Prince Caspian", "Prince Caspian - C. S. Lewis (2013).azw3"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/Prince Caspian", "Prince Caspian - C. S. Lewis (2013).epub"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/Prince Caspian", "Prince Caspian - C. S. Lewis (2013).mobi"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Horse and His Boy The Chronicles of Narnia", "The Horse and His Boy - C. S. Lewis.azw3"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Horse and His Boy The Chronicles of Narnia", "The Horse and His Boy - C. S. Lewis.epub"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Horse and His Boy The Chronicles of Narnia", "The Horse and His Boy - C. S. Lewis.mobi"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Magician's Nephew The Chronicles of Narnia", "The Magician's Nephew - C. S. Lewis (2013).epub"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Magician's Nephew The Chronicles of Narnia", "The Magician's Nephew - C. S. Lewis.azw3"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Magician's Nephew The Chronicles of Narnia", "The Magician's Nephew - C. S. Lewis.mobi"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Silver Chair", "The Silver Chair - C. S. Lewis (2013).epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(10);
        assertThat(totalFiles(groups)).isEqualTo(10);
    }

    @Test
    void structure8_bookPerFolder_deepNesting() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/Prince Caspian", "Prince Caspian - C. S. Lewis (2013).azw3"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/Prince Caspian", "Prince Caspian - C. S. Lewis (2013).epub"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/Prince Caspian", "Prince Caspian - C. S. Lewis (2013).mobi"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Horse and His Boy The Chronicles of Narnia", "The Horse and His Boy - C. S. Lewis.azw3"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Horse and His Boy The Chronicles of Narnia", "The Horse and His Boy - C. S. Lewis.epub"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Horse and His Boy The Chronicles of Narnia", "The Horse and His Boy - C. S. Lewis.mobi"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Magician's Nephew The Chronicles of Narnia", "The Magician's Nephew - C. S. Lewis (2013).epub"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Magician's Nephew The Chronicles of Narnia", "The Magician's Nephew - C. S. Lewis.azw3"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Magician's Nephew The Chronicles of Narnia", "The Magician's Nephew - C. S. Lewis.mobi"),
                ebook(path, "C. S. Lewis/The Chronicles of Narnia/The Silver Chair", "The Silver Chair - C. S. Lewis (2013).epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(4);
        assertThat(totalFiles(groups)).isEqualTo(10);
    }

    @Test
    void structure9_bookPerFile_largeSeries() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = new ArrayList<>();
        for (int i = 1; i <= 19; i++) {
            String name = String.format("Dune %02d - Title %d", i, i);
            files.add(ebook(path, "Dune/epub", name + ".epub"));
            files.add(ebook(path, "Dune/Mobi", name + ".mobi"));
        }

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(38);
        assertThat(totalFiles(groups)).isEqualTo(38);
    }

    @Test
    void structure9_bookPerFolder_largeSeries() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = new ArrayList<>();
        for (int i = 1; i <= 19; i++) {
            String name = String.format("Dune %02d - Title %d", i, i);
            files.add(ebook(path, "Dune/epub", name + ".epub"));
            files.add(ebook(path, "Dune/Mobi", name + ".mobi"));
        }

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(2);
        assertThat(totalFiles(groups)).isEqualTo(38);
    }

    @Test
    void structure10_bookPerFile_hpEbooks() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "J.K. Rowling/Harry Potter and the Chamber of Secrets", "Harry Potter and the Chamber of Secrets - J.K. Rowling (2019).fb2"),
                ebook(path, "J.K. Rowling/Harry Potter and the Chamber of Secrets", "Harry Potter and the Chamber of Secrets - J.K. Rowling (2019).pdf"),
                ebook(path, "J.K. Rowling/Harry Potter and the Chamber of Secrets", "Harry Potter and the Chamber of Secrets - J.K. Rowling.azw3"),
                ebook(path, "J.K. Rowling/Harry Potter and the Chamber of Secrets", "Harry Potter and the Chamber of Secrets - J.K. Rowling.epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Cursed Child", "Harry Potter and the Cursed Child - J.K. Rowling (2017).epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Deathly Hallows", "Harry Potter and the Deathly Hallows - J.K. Rowling.azw3"),
                ebook(path, "J.K. Rowling/Harry Potter and the Deathly Hallows", "Harry Potter and the Deathly Hallows - J.K. Rowling.epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Goblet of Fire", "Harry Potter and the Goblet of Fire - J.K. Rowling.epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Goblet of Fire", "Harry Potter and the Goblet of Fire - J.K. Rowling.mobi"),
                ebook(path, "J.K. Rowling/Harry Potter and the Goblet of Fire", "Harry Potter and the Goblet of Fire - J.K. Rowling.pdf"),
                ebook(path, "J.K. Rowling/Harry Potter and the Half-Blood Prince", "Harry Potter and the Half-Blood Prince - J.K. Rowling.epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Philosopher's Stone", "Harry Potter and the Philosopher's Stone - J.K. Rowling.azw3"),
                ebook(path, "J.K. Rowling/Harry Potter and the Philosopher's Stone", "Harry Potter and the Philosopher's Stone - J.K. Rowling.epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(13);
        assertThat(totalFiles(groups)).isEqualTo(13);
    }

    @Test
    void structure10_bookPerFolder_hpEbooks() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "J.K. Rowling/Harry Potter and the Chamber of Secrets", "Harry Potter and the Chamber of Secrets - J.K. Rowling (2019).fb2"),
                ebook(path, "J.K. Rowling/Harry Potter and the Chamber of Secrets", "Harry Potter and the Chamber of Secrets - J.K. Rowling (2019).pdf"),
                ebook(path, "J.K. Rowling/Harry Potter and the Chamber of Secrets", "Harry Potter and the Chamber of Secrets - J.K. Rowling.azw3"),
                ebook(path, "J.K. Rowling/Harry Potter and the Chamber of Secrets", "Harry Potter and the Chamber of Secrets - J.K. Rowling.epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Cursed Child", "Harry Potter and the Cursed Child - J.K. Rowling (2017).epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Deathly Hallows", "Harry Potter and the Deathly Hallows - J.K. Rowling.azw3"),
                ebook(path, "J.K. Rowling/Harry Potter and the Deathly Hallows", "Harry Potter and the Deathly Hallows - J.K. Rowling.epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Goblet of Fire", "Harry Potter and the Goblet of Fire - J.K. Rowling.epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Goblet of Fire", "Harry Potter and the Goblet of Fire - J.K. Rowling.mobi"),
                ebook(path, "J.K. Rowling/Harry Potter and the Goblet of Fire", "Harry Potter and the Goblet of Fire - J.K. Rowling.pdf"),
                ebook(path, "J.K. Rowling/Harry Potter and the Half-Blood Prince", "Harry Potter and the Half-Blood Prince - J.K. Rowling.epub"),
                ebook(path, "J.K. Rowling/Harry Potter and the Philosopher's Stone", "Harry Potter and the Philosopher's Stone - J.K. Rowling.azw3"),
                ebook(path, "J.K. Rowling/Harry Potter and the Philosopher's Stone", "Harry Potter and the Philosopher's Stone - J.K. Rowling.epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(6);
        assertThat(totalFiles(groups)).isEqualTo(13);
    }

    @Test
    void structure12_bookPerFile_fullAudiobooks() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "1 HARRY POTTER AND THE PHILOSOPHER'S STONE"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "2 HARRY POTTER AND THE CHAMBER OF SECRETS"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "3 HARRY POTTER AND THE PRISONER OF AZKABAN"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "4 HARRY POTTER AND THE GOBLET OF FIRE"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "5 HARRY POTTER ANND THE ORDER OF THE PHOENIX"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "6 HARRY POTTER AND THE HALF-BLOOD PRINCE"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "7 HARRY POTTER AND THE DEATHLY HALLOWS")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(7);
        assertThat(folderBasedCount(groups)).isEqualTo(7);
    }

    @Test
    void structure12_bookPerFolder_fullAudiobooks() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "1 HARRY POTTER AND THE PHILOSOPHER'S STONE"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "2 HARRY POTTER AND THE CHAMBER OF SECRETS"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "3 HARRY POTTER AND THE PRISONER OF AZKABAN"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "4 HARRY POTTER AND THE GOBLET OF FIRE"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "5 HARRY POTTER ANND THE ORDER OF THE PHOENIX"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "6 HARRY POTTER AND THE HALF-BLOOD PRINCE"),
                folderAudiobook(path, "J K Rowling - Harry Potter 1-7 Unabridged Audiobooks Narrated by Stephen Fry", "7 HARRY POTTER AND THE DEATHLY HALLOWS")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        // Each folder-based audiobook reconstructs effectiveSubPath = parentSubPath/folderName,
        // so with no ebook ancestor to absorb into, each stays separate
        assertThat(groupCount(groups)).isEqualTo(7);
        assertThat(totalFiles(groups)).isEqualTo(7);
        assertThat(folderBasedCount(groups)).isEqualTo(7);
    }

    @Test
    void structure13_bookPerFile_mismatchedNames() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Wendy Walker/Bladesssss", "Blade - Wendy Walddker.epub"),
                ebook(path, "Wendy Walker/Bladesssss", "Blade - Wendy Walker (2026).pdf")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(2);
    }

    @Test
    void structure13_bookPerFolder_mismatchedNames() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Wendy Walker/Bladesssss", "Blade - Wendy Walddker.epub"),
                ebook(path, "Wendy Walker/Bladesssss", "Blade - Wendy Walker (2026).pdf")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(1);
        assertThat(totalFiles(groups)).isEqualTo(2);
    }

    @Test
    void nullOrganizationMode_defaultsToAutoDetect() {
        var path = pathEntity(1L, "/books");
        var lib = library(null, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Author/Book", "Book - Author.epub"),
                ebook(path, "Author/Book", "Book - Author.mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(totalFiles(groups)).isEqualTo(2);
    }

    @Test
    void bookPerFolder_rootLevelAudiobooksAreIndividual() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                audiobook(path, "", "Audiobook One.m4b"),
                audiobook(path, "", "Audiobook Two.m4b"),
                ebook(path, "", "Some Book.epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(3);
    }

    @Test
    void bookPerFolder_audiobookAbsorbedIntoNearestEbookAncestor() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Author/Title", "Title - Author.epub"),
                folderAudiobook(path, "Author/Title/audiobook", "audiobook")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(1);
        assertThat(totalFiles(groups)).isEqualTo(2);
    }

    @Test
    void bookPerFolder_audiobookWithNoEbookAncestorStaysSeparate() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path);

        List<LibraryFile> files = List.of(
                folderAudiobook(path, "Audiobooks/Book One", "Book One"),
                ebook(path, "Ebooks/Some Book", "Some Book.epub")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(2);
    }

    @Test
    void bookPerFile_sameNameDifferentFormatsAreSeparateBooks() {
        var path = pathEntity(1L, "/books");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FILE, path);

        List<LibraryFile> files = List.of(
                ebook(path, "Author/Book", "Book.epub"),
                ebook(path, "Author/Book", "Book.mobi"),
                ebook(path, "Author/Book", "Book.pdf")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(3);
    }

    @Test
    void bookPerFolder_differentLibraryPathsAreNotMixed() {
        var path1 = pathEntity(1L, "/books1");
        var path2 = pathEntity(2L, "/books2");
        var lib = library(LibraryOrganizationMode.BOOK_PER_FOLDER, path1, path2);

        List<LibraryFile> files = List.of(
                ebook(path1, "Author/Title", "Book.epub"),
                ebook(path1, "Author/Title", "Book.mobi"),
                ebook(path2, "Author/Title", "Book.epub"),
                ebook(path2, "Author/Title", "Book.mobi")
        );

        var groups = bookGroupingService.groupForInitialScan(files, lib);

        assertThat(groupCount(groups)).isEqualTo(2);
        assertThat(totalFiles(groups)).isEqualTo(4);
    }
}
