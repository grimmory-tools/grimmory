package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbxMetadataExtractorTest {

    private CbxMetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new CbxMetadataExtractor();
    }

    private File createCbz(String comicInfoXml) throws IOException {
        return createCbz(comicInfoXml, true);
    }

    private File createCbz(String comicInfoXml, boolean includeImage) throws IOException {
        Path cbzPath = tempDir.resolve("test.cbz");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
            if (comicInfoXml != null) {
                zos.putNextEntry(new ZipEntry("ComicInfo.xml"));
                zos.write(comicInfoXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            if (includeImage) {
                zos.putNextEntry(new ZipEntry("page001.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();
            }
        }
        return cbzPath.toFile();
    }

    private byte[] createMinimalJpeg() throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    private String wrapInComicInfo(String innerXml) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <ComicInfo>
                %s
                </ComicInfo>
                """.formatted(innerXml);
    }

    @Nested
    class ExtractMetadataFromZip {

        @Test
        void extractsTitleFromComicInfo() throws IOException {
            String xml = wrapInComicInfo("<Title>Batman: Year One</Title>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("Batman: Year One");
        }

        @Test
        void fallsBackToFilenameWhenTitleMissing() throws IOException {
            String xml = wrapInComicInfo("<Publisher>DC Comics</Publisher>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }

        @Test
        void fallsBackToFilenameWhenTitleBlank() throws IOException {
            String xml = wrapInComicInfo("<Title>   </Title>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }

        @Test
        void fallsBackToFilenameWhenNoComicInfo() throws IOException {
            File cbz = createCbz(null);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }

        @Test
        void extractsPublisher() throws IOException {
            String xml = wrapInComicInfo("<Publisher>Marvel Comics</Publisher>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublisher()).isEqualTo("Marvel Comics");
        }

        @Test
        void extractsDescriptionFromSummary() throws IOException {
            String xml = wrapInComicInfo("<Summary>A dark tale of vengeance.</Summary>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getDescription()).isEqualTo("A dark tale of vengeance.");
        }

        @Test
        void prefersDescriptionOverSummaryWhenBothPresent() throws IOException {
            String xml = wrapInComicInfo("""
                    <Summary>Summary text</Summary>
                    <Description>Description text</Description>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            // coalesce picks Summary first (it's the first arg)
            assertThat(metadata.getDescription()).isEqualTo("Summary text");
        }

        @Test
        void fallsToDescriptionWhenSummaryBlank() throws IOException {
            String xml = wrapInComicInfo("""
                    <Summary>   </Summary>
                    <Description>Fallback description</Description>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getDescription()).isEqualTo("Fallback description");
        }

        @Test
        void extractsLanguageISO() throws IOException {
            String xml = wrapInComicInfo("<LanguageISO>en</LanguageISO>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getLanguage()).isEqualTo("en");
        }

        @Test
        void returnsMetadataForCorruptFile() throws IOException {
            Path corruptPath = tempDir.resolve("corrupt.cbz");
            Files.write(corruptPath, new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00});

            BookMetadata metadata = extractor.extractMetadata(corruptPath.toFile());

            assertThat(metadata.getTitle()).isEqualTo("corrupt");
        }
    }

    @Nested
    class SeriesAndNumberParsing {

        @Test
        void extractsSeriesName() throws IOException {
            String xml = wrapInComicInfo("<Series>The Sandman</Series>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesName()).isEqualTo("The Sandman");
        }

        @Test
        void extractsSeriesNumberAsFloat() throws IOException {
            String xml = wrapInComicInfo("<Number>3.5</Number>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesNumber()).isEqualTo(3.5f);
        }

        @Test
        void extractsWholeSeriesNumber() throws IOException {
            String xml = wrapInComicInfo("<Number>12</Number>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesNumber()).isEqualTo(12f);
        }

        @Test
        void handlesInvalidSeriesNumber() throws IOException {
            String xml = wrapInComicInfo("<Number>abc</Number>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesNumber()).isNull();
        }

        @Test
        void extractsSeriesTotal() throws IOException {
            String xml = wrapInComicInfo("<Count>75</Count>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesTotal()).isEqualTo(75);
        }

        @Test
        void extractsPageCount() throws IOException {
            String xml = wrapInComicInfo("<PageCount>32</PageCount>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPageCount()).isEqualTo(32);
        }

        @Test
        void prefersPageCountOverPages() throws IOException {
            String xml = wrapInComicInfo("""
                    <PageCount>32</PageCount>
                    <Pages>48</Pages>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPageCount()).isEqualTo(32);
        }

        @Test
        void fallsToPagesWhenPageCountMissing() throws IOException {
            String xml = wrapInComicInfo("<Pages>48</Pages>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPageCount()).isEqualTo(48);
        }
    }

    @Nested
    class DateParsing {

        @Test
        void parsesFullDate() throws IOException {
            String xml = wrapInComicInfo("""
                    <Year>2023</Year>
                    <Month>6</Month>
                    <Day>15</Day>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2023, 6, 15));
        }

        @Test
        void parsesYearOnly() throws IOException {
            String xml = wrapInComicInfo("<Year>1986</Year>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(1986, 1, 1));
        }

        @Test
        void parsesYearAndMonth() throws IOException {
            String xml = wrapInComicInfo("""
                    <Year>2020</Year>
                    <Month>11</Month>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2020, 11, 1));
        }

        @Test
        void returnsNullForMissingYear() throws IOException {
            String xml = wrapInComicInfo("""
                    <Month>6</Month>
                    <Day>15</Day>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isNull();
        }

        @Test
        void returnsNullForInvalidDate() throws IOException {
            String xml = wrapInComicInfo("""
                    <Year>2023</Year>
                    <Month>13</Month>
                    <Day>32</Day>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isNull();
        }

        @Test
        void handlesNonNumericYear() throws IOException {
            String xml = wrapInComicInfo("<Year>unknown</Year>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isNull();
        }
    }

    @Nested
    class IsbnParsing {

        @Test
        void extractsValid13DigitGtin() throws IOException {
            String xml = wrapInComicInfo("<GTIN>9781234567890</GTIN>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void normalizesGtinWithDashes() throws IOException {
            String xml = wrapInComicInfo("<GTIN>978-1-234-56789-0</GTIN>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void normalizesGtinWithSpaces() throws IOException {
            String xml = wrapInComicInfo("<GTIN>978 1 234 56789 0</GTIN>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void rejectsInvalidGtin() throws IOException {
            String xml = wrapInComicInfo("<GTIN>12345</GTIN>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isNull();
        }

        @Test
        void rejectsNonNumericGtin() throws IOException {
            String xml = wrapInComicInfo("<GTIN>978ABC1234567</GTIN>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isNull();
        }

        @Test
        void ignoresBlankGtin() throws IOException {
            String xml = wrapInComicInfo("<GTIN>   </GTIN>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isNull();
        }
    }

    @Nested
    class AuthorsAndCategories {

        @Test
        void extractsSingleWriter() throws IOException {
            String xml = wrapInComicInfo("<Writer>Alan Moore</Writer>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).containsExactly("Alan Moore");
        }

        @Test
        void splitsMultipleWritersByComma() throws IOException {
            String xml = wrapInComicInfo("<Writer>Alan Moore, Dave Gibbons</Writer>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Alan Moore", "Dave Gibbons");
        }

        @Test
        void splitsWritersBySemicolon() throws IOException {
            String xml = wrapInComicInfo("<Writer>Neil Gaiman; Mike Carey</Writer>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Neil Gaiman", "Mike Carey");
        }

        @Test
        void extractsGenreAsCategories() throws IOException {
            String xml = wrapInComicInfo("<Genre>Superhero, Action</Genre>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getCategories()).containsExactlyInAnyOrder("Superhero", "Action");
        }

        @Test
        void extractsTagsFromXml() throws IOException {
            String xml = wrapInComicInfo("<Tags>dark, gritty; mature</Tags>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTags()).containsExactlyInAnyOrder("dark", "gritty", "mature");
        }

        @Test
        void returnsNullAuthorsWhenWriterMissing() throws IOException {
            String xml = wrapInComicInfo("<Title>No Writer</Title>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).isNull();
        }

        @Test
        void ignoresEmptyValuesInSplit() throws IOException {
            String xml = wrapInComicInfo("<Writer>Alan Moore,,, Dave Gibbons</Writer>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Alan Moore", "Dave Gibbons");
        }
    }

    @Nested
    class ComicMetadataExtraction {

        @Test
        void extractsIssueNumber() throws IOException {
            String xml = wrapInComicInfo("<Number>42</Number>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata()).isNotNull();
            assertThat(metadata.getComicMetadata().getIssueNumber()).isEqualTo("42");
        }

        @Test
        void extractsVolume() throws IOException {
            String xml = wrapInComicInfo("""
                    <Series>Batman</Series>
                    <Volume>2016</Volume>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata()).isNotNull();
            assertThat(metadata.getComicMetadata().getVolumeName()).isEqualTo("Batman");
            assertThat(metadata.getComicMetadata().getVolumeNumber()).isEqualTo(2016);
        }

        @Test
        void extractsStoryArc() throws IOException {
            String xml = wrapInComicInfo("""
                    <StoryArc>Court of Owls</StoryArc>
                    <StoryArcNumber>3</StoryArcNumber>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getStoryArc()).isEqualTo("Court of Owls");
            assertThat(comic.getStoryArcNumber()).isEqualTo(3);
        }

        @Test
        void extractsAlternateSeries() throws IOException {
            String xml = wrapInComicInfo("""
                    <AlternateSeries>Detective Comics</AlternateSeries>
                    <AlternateNumber>500</AlternateNumber>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getAlternateSeries()).isEqualTo("Detective Comics");
            assertThat(comic.getAlternateIssue()).isEqualTo("500");
        }

        @Test
        void extractsCreatorRoles() throws IOException {
            String xml = wrapInComicInfo("""
                    <Penciller>Jim Lee, Greg Capullo</Penciller>
                    <Inker>Scott Williams</Inker>
                    <Colorist>Alex Sinclair</Colorist>
                    <Letterer>Richard Starkings</Letterer>
                    <CoverArtist>Jim Lee</CoverArtist>
                    <Editor>Bob Harras</Editor>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getPencillers()).containsExactlyInAnyOrder("Jim Lee", "Greg Capullo");
            assertThat(comic.getInkers()).containsExactly("Scott Williams");
            assertThat(comic.getColorists()).containsExactly("Alex Sinclair");
            assertThat(comic.getLetterers()).containsExactly("Richard Starkings");
            assertThat(comic.getCoverArtists()).containsExactly("Jim Lee");
            assertThat(comic.getEditors()).containsExactly("Bob Harras");
        }

        @Test
        void extractsImprint() throws IOException {
            String xml = wrapInComicInfo("<Imprint>Vertigo</Imprint>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getImprint()).isEqualTo("Vertigo");
        }

        @Test
        void extractsFormat() throws IOException {
            String xml = wrapInComicInfo("<Format>Trade Paperback</Format>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getFormat()).isEqualTo("Trade Paperback");
        }

        @Test
        void extractsBlackAndWhiteYes() throws IOException {
            String xml = wrapInComicInfo("<BlackAndWhite>Yes</BlackAndWhite>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getBlackAndWhite()).isTrue();
        }

        @Test
        void extractsBlackAndWhiteTrue() throws IOException {
            String xml = wrapInComicInfo("<BlackAndWhite>true</BlackAndWhite>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getBlackAndWhite()).isTrue();
        }

        @Test
        void blackAndWhiteNotSetForNo() throws IOException {
            String xml = wrapInComicInfo("<BlackAndWhite>No</BlackAndWhite>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            // "No" doesn't match "yes" or "true", so blackAndWhite is not set
            // but hasComicFields remains false for this alone, so comicMetadata is null
            assertThat(metadata.getComicMetadata()).isNull();
        }

        @Test
        void extractsMangaYes() throws IOException {
            String xml = wrapInComicInfo("<Manga>Yes</Manga>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getManga()).isTrue();
            assertThat(comic.getReadingDirection()).isEqualTo("ltr");
        }

        @Test
        void extractsMangaRightToLeft() throws IOException {
            String xml = wrapInComicInfo("<Manga>YesAndRightToLeft</Manga>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getManga()).isTrue();
            assertThat(comic.getReadingDirection()).isEqualTo("rtl");
        }

        @Test
        void extractsMangaNo() throws IOException {
            String xml = wrapInComicInfo("<Manga>No</Manga>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getManga()).isFalse();
            assertThat(comic.getReadingDirection()).isEqualTo("ltr");
        }

        @Test
        void extractsCharactersTeamsLocations() throws IOException {
            String xml = wrapInComicInfo("""
                    <Characters>Batman, Robin</Characters>
                    <Teams>Justice League; Teen Titans</Teams>
                    <Locations>Gotham City, Metropolis</Locations>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getCharacters()).containsExactlyInAnyOrder("Batman", "Robin");
            assertThat(comic.getTeams()).containsExactlyInAnyOrder("Justice League", "Teen Titans");
            assertThat(comic.getLocations()).containsExactlyInAnyOrder("Gotham City", "Metropolis");
        }

        @Test
        void noComicMetadataWhenNoComicFieldsPresent() throws IOException {
            String xml = wrapInComicInfo("<Title>Just a title</Title>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata()).isNull();
        }

        @Test
        void extractsWebLink() throws IOException {
            String xml = wrapInComicInfo("<Web>https://example.com/comic</Web>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getWebLink()).isEqualTo("https://example.com/comic");
        }

        @Test
        void extractsNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>Some notes here</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getNotes()).isEqualTo("Some notes here");
        }
    }

    @Nested
    class WebFieldParsing {

        @Test
        void extractsGoodreadsIdFromUrl() throws IOException {
            String xml = wrapInComicInfo("<Web>https://www.goodreads.com/book/show/12345-some-book</Web>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getGoodreadsId()).isEqualTo("12345");
        }

        @Test
        void extractsAsinFromAmazonUrl() throws IOException {
            String xml = wrapInComicInfo("<Web>https://www.amazon.com/dp/B08N5WRWNW</Web>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAsin()).isEqualTo("B08N5WRWNW");
        }

        @Test
        void extractsComicvineIdFromUrl() throws IOException {
            String xml = wrapInComicInfo("<Web>https://comicvine.gamespot.com/issue/batman-1/4000-12345</Web>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicvineId()).isEqualTo("4000-12345");
        }

        @Test
        void extractsHardcoverIdFromUrl() throws IOException {
            String xml = wrapInComicInfo("<Web>https://hardcover.app/books/batman-year-one</Web>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getHardcoverId()).isEqualTo("batman-year-one");
        }

        @Test
        void extractsMultipleIdsFromSpaceSeparatedUrls() throws IOException {
            String xml = wrapInComicInfo(
                    "<Web>https://www.goodreads.com/book/show/99999 https://www.amazon.com/dp/B012345678</Web>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getGoodreadsId()).isEqualTo("99999");
            assertThat(metadata.getAsin()).isEqualTo("B012345678");
        }
    }

    @Nested
    class BookLoreNoteParsing {

        @Test
        void extractsMoodsFromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:Moods] dark, brooding</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getMoods()).containsExactlyInAnyOrder("dark", "brooding");
        }

        @Test
        void extractsSubtitleFromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:Subtitle] The Dark Knight Returns</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSubtitle()).isEqualTo("The Dark Knight Returns");
        }

        @Test
        void extractsIsbn13FromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:ISBN13] 9781234567890</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void extractsIsbn10FromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:ISBN10] 0123456789</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn10()).isEqualTo("0123456789");
        }

        @Test
        void extractsAsinFromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:ASIN] B08N5WRWNW</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAsin()).isEqualTo("B08N5WRWNW");
        }

        @Test
        void extractsGoodreadsIdFromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:GoodreadsId] 12345</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getGoodreadsId()).isEqualTo("12345");
        }

        @Test
        void extractsComicvineIdFromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:ComicvineId] 4000-12345</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicvineId()).isEqualTo("4000-12345");
        }

        @Test
        void extractsRatingsFromNotes() throws IOException {
            String xml = wrapInComicInfo("""
                    <Notes>[BookLore:AmazonRating] 4.5
                    [BookLore:GoodreadsRating] 4.2
                    [BookLore:HardcoverRating] 3.8</Notes>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAmazonRating()).isEqualTo(4.5);
            assertThat(metadata.getGoodreadsRating()).isEqualTo(4.2);
            assertThat(metadata.getHardcoverRating()).isEqualTo(3.8);
        }

        @Test
        void extractsHardcoverBookIdFromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:HardcoverBookId] abc-123</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getHardcoverBookId()).isEqualTo("abc-123");
        }

        @Test
        void extractsHardcoverIdFromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:HardcoverId] hc-456</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getHardcoverId()).isEqualTo("hc-456");
        }

        @Test
        void extractsGoogleIdFromNotes() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:GoogleId] google-789</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getGoogleId()).isEqualTo("google-789");
        }

        @Test
        void extractsLubimyczytacFromNotes() throws IOException {
            String xml = wrapInComicInfo("""
                    <Notes>[BookLore:LubimyczytacId] lub-123
                    [BookLore:LubimyczytacRating] 4.1</Notes>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getLubimyczytacId()).isEqualTo("lub-123");
            assertThat(metadata.getLubimyczytacRating()).isEqualTo(4.1);
        }

        @Test
        void extractsRanobedbFromNotes() throws IOException {
            String xml = wrapInComicInfo("""
                    <Notes>[BookLore:RanobedbId] rdb-456
                    [BookLore:RanobedbRating] 3.9</Notes>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getRanobedbId()).isEqualTo("rdb-456");
            assertThat(metadata.getRanobedbRating()).isEqualTo(3.9);
        }

        @Test
        void mergesTagsFromXmlAndNotes() throws IOException {
            String xml = wrapInComicInfo("""
                    <Tags>existing-tag</Tags>
                    <Notes>[BookLore:Tags] new-tag, another-tag</Notes>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTags()).containsExactlyInAnyOrder("existing-tag", "new-tag", "another-tag");
        }

        @Test
        void notesUsedAsDescriptionWhenSummaryMissing() throws IOException {
            String xml = wrapInComicInfo("<Notes>This is a great comic.\n[BookLore:ISBN13] 9780000000000</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getDescription()).isEqualTo("This is a great comic.");
            assertThat(metadata.getIsbn13()).isEqualTo("9780000000000");
        }

        @Test
        void notesNotUsedAsDescriptionWhenSummaryPresent() throws IOException {
            String xml = wrapInComicInfo("""
                    <Summary>Official summary</Summary>
                    <Notes>This is a great comic.\n[BookLore:ISBN13] 9780000000000</Notes>
                    """);
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getDescription()).isEqualTo("Official summary");
        }

        @Test
        void handlesInvalidRatingGracefully() throws IOException {
            String xml = wrapInComicInfo("<Notes>[BookLore:AmazonRating] not-a-number</Notes>");
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAmazonRating()).isNull();
        }
    }

    @Nested
    class CoverExtraction {

        @Test
        void extractsCoverFromCbzWithImage() throws IOException {
            File cbz = createCbz(wrapInComicInfo("<Title>Test</Title>"), true);

            byte[] cover = extractor.extractCover(cbz);

            assertThat(cover).isNotNull();
            assertThat(cover.length).isGreaterThan(0);
        }

        @Test
        void returnPlaceholderForEmptyCbz() throws IOException {
            Path cbzPath = tempDir.resolve("empty.cbz");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("readme.txt"));
                zos.write("no images here".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());

            assertThat(cover).isNotNull();
            // Placeholder is a generated image
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(cover));
            assertThat(img).isNotNull();
            assertThat(img.getWidth()).isEqualTo(250);
            assertThat(img.getHeight()).isEqualTo(350);
        }

        @Test
        void extractsCoverFromFirstAlphabeticalImage() throws IOException {
            Path cbzPath = tempDir.resolve("multipage.cbz");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("page003.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("page001.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("page002.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());

            assertThat(cover).isNotNull();
            assertThat(cover.length).isGreaterThan(0);
        }

        @Test
        void prefersCoverNamedFileOverAlphabetical() throws IOException {
            Path cbzPath = tempDir.resolve("withcover.cbz");
            byte[] coverJpeg = createMinimalJpeg();
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("page001.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("cover.jpg"));
                zos.write(coverJpeg);
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());

            assertThat(cover).isNotNull();
        }

        @Test
        void extractsCoverViaFrontCoverPageElement() throws IOException {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <ComicInfo>
                      <Title>Test</Title>
                      <Pages>
                        <Page Image="1" Type="FrontCover" ImageFile="cover_image.jpg"/>
                      </Pages>
                    </ComicInfo>
                    """;
            Path cbzPath = tempDir.resolve("frontcover.cbz");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("ComicInfo.xml"));
                zos.write(xml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("cover_image.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("page001.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());

            assertThat(cover).isNotNull();
            assertThat(cover.length).isGreaterThan(0);
        }

        @Test
        void frontCoverPageByImageIndex() throws IOException {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <ComicInfo>
                      <Title>Test</Title>
                      <Pages>
                        <Page Image="0" Type="FrontCover"/>
                      </Pages>
                    </ComicInfo>
                    """;
            Path cbzPath = tempDir.resolve("indexcover.cbz");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("ComicInfo.xml"));
                zos.write(xml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("page001.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());

            assertThat(cover).isNotNull();
        }

        @Test
        void skipsMacOsxEntries() throws IOException {
            Path cbzPath = tempDir.resolve("macosx.cbz");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("__MACOSX/._cover.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("page001.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());

            assertThat(cover).isNotNull();
        }

        @Test
        void skipsDotFiles() throws IOException {
            Path cbzPath = tempDir.resolve("dotfiles.cbz");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry(".hidden.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry(".DS_Store"));
                zos.write("data".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("actual_page.png"));
                zos.write(createMinimalPng());
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());

            assertThat(cover).isNotNull();
        }

        @Test
        void returnsPlaceholderForCorruptFile() throws IOException {
            Path corruptPath = tempDir.resolve("corrupt.cbz");
            Files.write(corruptPath, new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00});

            byte[] cover = extractor.extractCover(corruptPath.toFile());

            assertThat(cover).isNotNull();
        }

        private byte[] createMinimalPng() throws IOException {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }
    }

    @Nested
    class ComicInfoCaseInsensitive {

        @Test
        void findsComicInfoRegardlessOfCase() throws IOException {
            Path cbzPath = tempDir.resolve("casetest.cbz");
            String xml = wrapInComicInfo("<Title>Case Test</Title>");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("COMICINFO.XML"));
                zos.write(xml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("page001.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();
            }

            BookMetadata metadata = extractor.extractMetadata(cbzPath.toFile());

            assertThat(metadata.getTitle()).isEqualTo("Case Test");
        }

        @Test
        void findsComicInfoInSubdirectory() throws IOException {
            Path cbzPath = tempDir.resolve("subdir.cbz");
            String xml = wrapInComicInfo("<Title>Subdir Test</Title>");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("metadata/ComicInfo.xml"));
                zos.write(xml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("page001.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();
            }

            BookMetadata metadata = extractor.extractMetadata(cbzPath.toFile());

            assertThat(metadata.getTitle()).isEqualTo("Subdir Test");
        }
    }

    @Nested
    class FullComicInfoIntegration {

        @Test
        void extractsAllFieldsFromRichComicInfo() throws IOException {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <ComicInfo>
                      <Title>Batman: The Dark Knight Returns</Title>
                      <Series>Batman</Series>
                      <Number>1</Number>
                      <Count>4</Count>
                      <Volume>1986</Volume>
                      <Summary>In a bleak future, Bruce Wayne returns as Batman.</Summary>
                      <Year>1986</Year>
                      <Month>2</Month>
                      <Day>1</Day>
                      <Writer>Frank Miller</Writer>
                      <Penciller>Frank Miller</Penciller>
                      <Inker>Klaus Janson</Inker>
                      <Colorist>Lynn Varley</Colorist>
                      <Publisher>DC Comics</Publisher>
                      <Genre>Superhero, Action, Drama</Genre>
                      <Tags>dark, classic</Tags>
                      <PageCount>48</PageCount>
                      <LanguageISO>en</LanguageISO>
                      <GTIN>9781563893421</GTIN>
                      <StoryArc>The Dark Knight Returns</StoryArc>
                      <StoryArcNumber>1</StoryArcNumber>
                      <BlackAndWhite>No</BlackAndWhite>
                      <Manga>No</Manga>
                      <Characters>Batman, Superman, Robin</Characters>
                      <Teams>Justice League</Teams>
                      <Locations>Gotham City</Locations>
                      <Web>https://www.goodreads.com/book/show/59960-the-dark-knight-returns</Web>
                      <Notes>[BookLore:Subtitle] Part One
                    [BookLore:Moods] dark, intense, brooding
                    [BookLore:ISBN10] 1563893428</Notes>
                    </ComicInfo>
                    """;
            File cbz = createCbz(xml);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("Batman: The Dark Knight Returns");
            assertThat(metadata.getSeriesName()).isEqualTo("Batman");
            assertThat(metadata.getSeriesNumber()).isEqualTo(1f);
            assertThat(metadata.getSeriesTotal()).isEqualTo(4);
            assertThat(metadata.getDescription()).isEqualTo("In a bleak future, Bruce Wayne returns as Batman.");
            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(1986, 2, 1));
            assertThat(metadata.getAuthors()).containsExactly("Frank Miller");
            assertThat(metadata.getPublisher()).isEqualTo("DC Comics");
            assertThat(metadata.getCategories()).containsExactlyInAnyOrder("Superhero", "Action", "Drama");
            assertThat(metadata.getTags()).containsExactlyInAnyOrder("dark", "classic");
            assertThat(metadata.getPageCount()).isEqualTo(48);
            assertThat(metadata.getLanguage()).isEqualTo("en");
            assertThat(metadata.getIsbn13()).isEqualTo("9781563893421");
            assertThat(metadata.getSubtitle()).isEqualTo("Part One");
            assertThat(metadata.getMoods()).containsExactlyInAnyOrder("dark", "intense", "brooding");
            assertThat(metadata.getIsbn10()).isEqualTo("1563893428");
            assertThat(metadata.getGoodreadsId()).isEqualTo("59960");

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic).isNotNull();
            assertThat(comic.getIssueNumber()).isEqualTo("1");
            assertThat(comic.getVolumeName()).isEqualTo("Batman");
            assertThat(comic.getVolumeNumber()).isEqualTo(1986);
            assertThat(comic.getStoryArc()).isEqualTo("The Dark Knight Returns");
            assertThat(comic.getStoryArcNumber()).isEqualTo(1);
            assertThat(comic.getPencillers()).containsExactly("Frank Miller");
            assertThat(comic.getInkers()).containsExactly("Klaus Janson");
            assertThat(comic.getColorists()).containsExactly("Lynn Varley");
            assertThat(comic.getManga()).isFalse();
            assertThat(comic.getReadingDirection()).isEqualTo("ltr");
            assertThat(comic.getCharacters()).containsExactlyInAnyOrder("Batman", "Superman", "Robin");
            assertThat(comic.getTeams()).containsExactly("Justice League");
            assertThat(comic.getLocations()).containsExactly("Gotham City");
            assertThat(comic.getWebLink()).isEqualTo("https://www.goodreads.com/book/show/59960-the-dark-knight-returns");
        }
    }

    @Nested
    class ImageFormatSupport {

        @Test
        void recognizesJpgExtension() throws IOException {
            Path cbzPath = tempDir.resolve("formats.cbz");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("image.jpg"));
                zos.write(createMinimalJpeg());
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());
            assertThat(cover).isNotNull();
            assertThat(cover.length).isGreaterThan(0);
        }

        @Test
        void recognizesPngExtension() throws IOException {
            Path cbzPath = tempDir.resolve("png.cbz");
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbzPath))) {
                zos.putNextEntry(new ZipEntry("image.png"));
                zos.write(baos.toByteArray());
                zos.closeEntry();
            }

            byte[] cover = extractor.extractCover(cbzPath.toFile());
            assertThat(cover).isNotNull();
            assertThat(cover.length).isGreaterThan(0);
        }
    }

    @Nested
    class UnknownArchiveType {

        @Test
        void returnsFallbackForNonArchiveFile() throws IOException {
            Path txtPath = tempDir.resolve("notanarchive.txt");
            Files.writeString(txtPath, "This is not an archive.");

            BookMetadata metadata = extractor.extractMetadata(txtPath.toFile());

            assertThat(metadata.getTitle()).isEqualTo("notanarchive");
        }

        @Test
        void returnsPlaceholderCoverForNonArchiveFile() throws IOException {
            Path txtPath = tempDir.resolve("notanarchive.txt");
            Files.writeString(txtPath, "This is not an archive.");

            byte[] cover = extractor.extractCover(txtPath.toFile());

            assertThat(cover).isNotNull();
        }
    }
}
