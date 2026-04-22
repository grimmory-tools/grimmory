package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.service.ArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CbxMetadataExtractorTest {
    @Mock private ArchiveService archiveService;
    private CbxMetadataExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new CbxMetadataExtractor(archiveService);
    }

    private byte[] createMinimalJpeg(int rgb) throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, rgb);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    private byte[] createMinimalPng() throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
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

    private Path mockArchiveContents(Map<String, byte[]> contents) throws IOException {
        Path path = Path.of("test.cbz");
        Set<String> keys = contents.keySet();
        when(archiveService.streamEntryNames(path)).then((i) -> keys.stream());

        for (String key : keys) {
            when(archiveService.getEntryBytes(path, key)).thenReturn(contents.get(key));
        }

        return path;
    }

    private Path mockRaisesException() throws IOException {
        Path path = Path.of("test.cbz");
        when(archiveService.streamEntryNames(path)).thenThrow(IOException.class);
        when(archiveService.getEntryBytes(path, "ComicInfo.xml")).thenThrow(IOException.class);
        return path;
    }

    private Path mockEmptyArchive() throws IOException {
        Path path = Path.of("test.cbz");
        when(archiveService.streamEntryNames(path)).then((i) -> Stream.empty());
        when(archiveService.getEntryBytes(eq(path), any())).thenThrow(IOException.class);
        return path;
    }

    private Path mockComicInfo(String innerXml) throws IOException {
        Path path = Path.of("test.cbz");
        String xml = wrapInComicInfo(innerXml);
        when(archiveService.getEntryBytes(path, "ComicInfo.xml")).thenReturn(xml.getBytes());
        when(archiveService.streamEntryNames(path)).then((i) -> Stream.of("ComicInfo.xml"));

        return path;
    }

    @Nested
    class ExtractMetadataFromZip {

        @Test
        void extractsTitleFromComicInfo() throws IOException {
            Path cbz = mockComicInfo("<Title>Batman: Year One</Title>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("Batman: Year One");
        }

        @Test
        void fallsBackToFilenameWhenTitleMissing() throws IOException {
            Path cbz = mockComicInfo("<Publisher>DC Comics</Publisher>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }

        @Test
        void fallsBackToFilenameWhenTitleBlank() throws IOException {
            Path cbz = mockComicInfo("<Title>   </Title>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }

        @Test
        void fallsBackToFilenameWhenNoComicInfo() throws IOException {
            Path cbz = mockEmptyArchive();

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }

        @Test
        void extractsPublisher() throws IOException {
            Path cbz = mockComicInfo("<Publisher>Marvel Comics</Publisher>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublisher()).isEqualTo("Marvel Comics");
        }

        @Test
        void extractsDescriptionFromSummary() throws IOException {
            Path cbz = mockComicInfo("<Summary>A dark tale of vengeance.</Summary>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getDescription()).isEqualTo("A dark tale of vengeance.");
        }

        @Test
        void prefersDescriptionOverSummaryWhenBothPresent() throws IOException {
            Path cbz = mockComicInfo("""
                    <Summary>Summary text</Summary>
                    <Description>Description text</Description>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            // coalesce picks Summary first (it's the first arg)
            assertThat(metadata.getDescription()).isEqualTo("Summary text");
        }

        @Test
        void fallsToDescriptionWhenSummaryBlank() throws IOException {
            Path cbz = mockComicInfo("""
                    <Summary>   </Summary>
                    <Description>Fallback description</Description>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getDescription()).isEqualTo("Fallback description");
        }

        @Test
        void extractsLanguageISO() throws IOException {
            Path cbz = mockComicInfo("<LanguageISO>en</LanguageISO>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getLanguage()).isEqualTo("en");
        }

        @Test
        void returnsMetadataForCorruptFile() throws IOException {
            Path path = mockRaisesException();

            BookMetadata metadata = extractor.extractMetadata(path);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }
    }

    @Nested
    class SeriesAndNumberParsing {

        @Test
        void extractsSeriesName() throws IOException {
            Path cbz = mockComicInfo("<Series>The Sandman</Series>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesName()).isEqualTo("The Sandman");
        }

        @Test
        void extractsSeriesNumberAsFloat() throws IOException {
            Path cbz = mockComicInfo("<Number>3.5</Number>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesNumber()).isEqualTo(3.5f);
        }

        @Test
        void extractsWholeSeriesNumber() throws IOException {
            Path cbz = mockComicInfo("<Number>12</Number>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesNumber()).isEqualTo(12f);
        }

        @Test
        void handlesInvalidSeriesNumber() throws IOException {
            Path cbz = mockComicInfo("<Number>abc</Number>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesNumber()).isNull();
        }

        @Test
        void extractsSeriesTotal() throws IOException {
            Path cbz = mockComicInfo("<Count>75</Count>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSeriesTotal()).isEqualTo(75);
        }

        @Test
        void extractsPageCount() throws IOException {
            Path cbz = mockComicInfo("<PageCount>32</PageCount>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPageCount()).isEqualTo(32);
        }

        @Test
        void prefersPageCountOverPages() throws IOException {
            Path cbz = mockComicInfo("""
                    <PageCount>32</PageCount>
                    <Pages>48</Pages>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPageCount()).isEqualTo(32);
        }

        @Test
        void fallsToPagesWhenPageCountMissing() throws IOException {
            Path cbz = mockComicInfo("<Pages>48</Pages>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPageCount()).isEqualTo(48);
        }
    }

    @Nested
    class DateParsing {

        @Test
        void parsesFullDate() throws IOException {
            Path cbz = mockComicInfo("""
                    <Year>2023</Year>
                    <Month>6</Month>
                    <Day>15</Day>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2023, 6, 15));
        }

        @Test
        void parsesYearOnly() throws IOException {
            Path cbz = mockComicInfo("<Year>1986</Year>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(1986, 1, 1));
        }

        @Test
        void parsesYearAndMonth() throws IOException {
            Path cbz = mockComicInfo("""
                    <Year>2020</Year>
                    <Month>11</Month>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2020, 11, 1));
        }

        @Test
        void returnsNullForMissingYear() throws IOException {
            Path cbz = mockComicInfo("""
                    <Month>6</Month>
                    <Day>15</Day>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isNull();
        }

        @Test
        void returnsNullForInvalidDate() throws IOException {
            Path cbz = mockComicInfo("""
                    <Year>2023</Year>
                    <Month>13</Month>
                    <Day>32</Day>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isNull();
        }

        @Test
        void handlesNonNumericYear() throws IOException {
            Path cbz = mockComicInfo("<Year>unknown</Year>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getPublishedDate()).isNull();
        }
    }

    @Nested
    class IsbnParsing {

        @Test
        void extractsValid13DigitGtin() throws IOException {
            Path cbz = mockComicInfo("<GTIN>9781234567890</GTIN>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void normalizesGtinWithDashes() throws IOException {
            Path cbz = mockComicInfo("<GTIN>978-1-234-56789-0</GTIN>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void normalizesGtinWithSpaces() throws IOException {
            Path cbz = mockComicInfo("<GTIN>978 1 234 56789 0</GTIN>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void rejectsInvalidGtin() throws IOException {
            Path cbz = mockComicInfo("<GTIN>12345</GTIN>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isNull();
        }

        @Test
        void rejectsNonNumericGtin() throws IOException {
            Path cbz = mockComicInfo("<GTIN>978ABC1234567</GTIN>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isNull();
        }

        @Test
        void ignoresBlankGtin() throws IOException {
            Path cbz = mockComicInfo("<GTIN>   </GTIN>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isNull();
        }
    }

    @Nested
    class AuthorsAndCategories {

        @Test
        void extractsSingleWriter() throws IOException {
            Path cbz = mockComicInfo("<Writer>Alan Moore</Writer>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).containsExactly("Alan Moore");
        }

        @Test
        void splitsMultipleWritersByComma() throws IOException {
            Path cbz = mockComicInfo("<Writer>Alan Moore, Dave Gibbons</Writer>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Alan Moore", "Dave Gibbons");
        }

        @Test
        void splitsWritersBySemicolon() throws IOException {
            Path cbz = mockComicInfo("<Writer>Neil Gaiman; Mike Carey</Writer>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Neil Gaiman", "Mike Carey");
        }

        @Test
        void extractsGenreAsCategories() throws IOException {
            Path cbz = mockComicInfo("<Genre>Superhero, Action</Genre>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getCategories()).containsExactlyInAnyOrder("Superhero", "Action");
        }

        @Test
        void extractsTagsFromXml() throws IOException {
            Path cbz = mockComicInfo("<Tags>dark, gritty; mature</Tags>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTags()).containsExactlyInAnyOrder("dark", "gritty", "mature");
        }

        @Test
        void returnsNullAuthorsWhenWriterMissing() throws IOException {
            Path cbz = mockComicInfo("<Title>No Writer</Title>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).isNull();
        }

        @Test
        void ignoresEmptyValuesInSplit() throws IOException {
            Path cbz = mockComicInfo("<Writer>Alan Moore,,, Dave Gibbons</Writer>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Alan Moore", "Dave Gibbons");
        }
    }

    @Nested
    class ComicMetadataExtraction {

        @Test
        void extractsIssueNumber() throws IOException {
            Path cbz = mockComicInfo("<Number>42</Number>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata()).isNotNull();
            assertThat(metadata.getComicMetadata().getIssueNumber()).isEqualTo("42");
        }

        @Test
        void extractsVolume() throws IOException {
            Path cbz = mockComicInfo("""
                    <Series>Batman</Series>
                    <Volume>2016</Volume>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata()).isNotNull();
            assertThat(metadata.getComicMetadata().getVolumeName()).isEqualTo("Batman");
            assertThat(metadata.getComicMetadata().getVolumeNumber()).isEqualTo(2016);
        }

        @Test
        void extractsStoryArc() throws IOException {
            Path cbz = mockComicInfo("""
                    <StoryArc>Court of Owls</StoryArc>
                    <StoryArcNumber>3</StoryArcNumber>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getStoryArc()).isEqualTo("Court of Owls");
            assertThat(comic.getStoryArcNumber()).isEqualTo(3);
        }

        @Test
        void extractsAlternateSeries() throws IOException {
            Path cbz = mockComicInfo("""
                    <AlternateSeries>Detective Comics</AlternateSeries>
                    <AlternateNumber>500</AlternateNumber>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getAlternateSeries()).isEqualTo("Detective Comics");
            assertThat(comic.getAlternateIssue()).isEqualTo("500");
        }

        @Test
        void extractsCreatorRoles() throws IOException {
            Path cbz = mockComicInfo("""
                    <Penciller>Jim Lee, Greg Capullo</Penciller>
                    <Inker>Scott Williams</Inker>
                    <Colorist>Alex Sinclair</Colorist>
                    <Letterer>Richard Starkings</Letterer>
                    <CoverArtist>Jim Lee</CoverArtist>
                    <Editor>Bob Harras</Editor>
                    """);

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
            Path cbz = mockComicInfo("<Imprint>Vertigo</Imprint>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getImprint()).isEqualTo("Vertigo");
        }

        @Test
        void extractsFormat() throws IOException {
            Path cbz = mockComicInfo("<Format>Trade Paperback</Format>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getFormat()).isEqualTo("Trade Paperback");
        }

        @Test
        void extractsBlackAndWhiteYes() throws IOException {
            Path cbz = mockComicInfo("<BlackAndWhite>Yes</BlackAndWhite>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getBlackAndWhite()).isTrue();
        }

        @Test
        void extractsBlackAndWhiteTrue() throws IOException {
            Path cbz = mockComicInfo("<BlackAndWhite>true</BlackAndWhite>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getBlackAndWhite()).isTrue();
        }

        @Test
        void blackAndWhiteNotSetForNo() throws IOException {
            Path cbz = mockComicInfo("<BlackAndWhite>No</BlackAndWhite>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            // "No" doesn't match "yes" or "true", so blackAndWhite is not set
            // but hasComicFields remains false for this alone, so comicMetadata is null
            assertThat(metadata.getComicMetadata()).isNull();
        }

        @Test
        void extractsMangaYes() throws IOException {
            Path cbz = mockComicInfo("<Manga>Yes</Manga>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getManga()).isTrue();
            assertThat(comic.getReadingDirection()).isEqualTo("ltr");
        }

        @Test
        void extractsMangaRightToLeft() throws IOException {
            Path cbz = mockComicInfo("<Manga>YesAndRightToLeft</Manga>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getManga()).isTrue();
            assertThat(comic.getReadingDirection()).isEqualTo("rtl");
        }

        @Test
        void extractsMangaNo() throws IOException {
            Path cbz = mockComicInfo("<Manga>No</Manga>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getManga()).isFalse();
            assertThat(comic.getReadingDirection()).isEqualTo("ltr");
        }

        @Test
        void extractsCharactersTeamsLocations() throws IOException {
            Path cbz = mockComicInfo("""
                    <Characters>Batman, Robin</Characters>
                    <Teams>Justice League; Teen Titans</Teams>
                    <Locations>Gotham City, Metropolis</Locations>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            ComicMetadata comic = metadata.getComicMetadata();
            assertThat(comic.getCharacters()).containsExactlyInAnyOrder("Batman", "Robin");
            assertThat(comic.getTeams()).containsExactlyInAnyOrder("Justice League", "Teen Titans");
            assertThat(comic.getLocations()).containsExactlyInAnyOrder("Gotham City", "Metropolis");
        }

        @Test
        void noComicMetadataWhenNoComicFieldsPresent() throws IOException {
            Path cbz = mockComicInfo("<Title>Just a title</Title>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata()).isNull();
        }

        @Test
        void extractsWebLink() throws IOException {
            Path cbz = mockComicInfo("<Web>https://example.com/comic</Web>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getWebLink()).isEqualTo("https://example.com/comic");
        }

        @Test
        void extractsNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>Some notes here</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicMetadata().getNotes()).isEqualTo("Some notes here");
        }
    }

    @Nested
    class WebFieldParsing {

        @Test
        void extractsGoodreadsIdFromUrl() throws IOException {
            Path cbz = mockComicInfo("<Web>https://www.goodreads.com/book/show/12345-some-book</Web>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getGoodreadsId()).isEqualTo("12345");
        }

        @Test
        void extractsAsinFromAmazonUrl() throws IOException {
            Path cbz = mockComicInfo("<Web>https://www.amazon.com/dp/B08N5WRWNW</Web>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAsin()).isEqualTo("B08N5WRWNW");
        }

        @Test
        void extractsComicvineIdFromUrl() throws IOException {
            Path cbz = mockComicInfo("<Web>https://comicvine.gamespot.com/issue/batman-1/4000-12345</Web>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicvineId()).isEqualTo("4000-12345");
        }

        @Test
        void extractsHardcoverIdFromUrl() throws IOException {
            Path cbz = mockComicInfo("<Web>https://hardcover.app/books/batman-year-one</Web>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getHardcoverId()).isEqualTo("batman-year-one");
        }

        @Test
        void extractsMultipleIdsFromSpaceSeparatedUrls() throws IOException {
            Path cbz = mockComicInfo(
                    "<Web>https://www.goodreads.com/book/show/99999 https://www.amazon.com/dp/B012345678</Web>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getGoodreadsId()).isEqualTo("99999");
            assertThat(metadata.getAsin()).isEqualTo("B012345678");
        }
    }

    @Nested
    class BookLoreNoteParsing {

        @Test
        void extractsMoodsFromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:Moods] dark, brooding</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getMoods()).containsExactlyInAnyOrder("dark", "brooding");
        }

        @Test
        void extractsSubtitleFromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:Subtitle] The Dark Knight Returns</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getSubtitle()).isEqualTo("The Dark Knight Returns");
        }

        @Test
        void extractsIsbn13FromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:ISBN13] 9781234567890</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void extractsIsbn10FromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:ISBN10] 0123456789</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getIsbn10()).isEqualTo("0123456789");
        }

        @Test
        void extractsAsinFromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:ASIN] B08N5WRWNW</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAsin()).isEqualTo("B08N5WRWNW");
        }

        @Test
        void extractsGoodreadsIdFromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:GoodreadsId] 12345</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getGoodreadsId()).isEqualTo("12345");
        }

        @Test
        void extractsComicvineIdFromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:ComicvineId] 4000-12345</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getComicvineId()).isEqualTo("4000-12345");
        }

        @Test
        void extractsRatingsFromNotes() throws IOException {
            Path cbz = mockComicInfo("""
                    <Notes>[BookLore:AmazonRating] 4.5
                    [BookLore:GoodreadsRating] 4.2
                    [BookLore:HardcoverRating] 3.8</Notes>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAmazonRating()).isEqualTo(4.5);
            assertThat(metadata.getGoodreadsRating()).isEqualTo(4.2);
            assertThat(metadata.getHardcoverRating()).isEqualTo(3.8);
        }

        @Test
        void extractsHardcoverBookIdFromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:HardcoverBookId] abc-123</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getHardcoverBookId()).isEqualTo("abc-123");
        }

        @Test
        void extractsHardcoverIdFromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:HardcoverId] hc-456</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getHardcoverId()).isEqualTo("hc-456");
        }

        @Test
        void extractsGoogleIdFromNotes() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:GoogleId] google-789</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getGoogleId()).isEqualTo("google-789");
        }

        @Test
        void extractsLubimyczytacFromNotes() throws IOException {
            Path cbz = mockComicInfo("""
                    <Notes>[BookLore:LubimyczytacId] lub-123
                    [BookLore:LubimyczytacRating] 4.1</Notes>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getLubimyczytacId()).isEqualTo("lub-123");
            assertThat(metadata.getLubimyczytacRating()).isEqualTo(4.1);
        }

        @Test
        void extractsRanobedbFromNotes() throws IOException {
            Path cbz = mockComicInfo("""
                    <Notes>[BookLore:RanobedbId] rdb-456
                    [BookLore:RanobedbRating] 3.9</Notes>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getRanobedbId()).isEqualTo("rdb-456");
            assertThat(metadata.getRanobedbRating()).isEqualTo(3.9);
        }

        @Test
        void mergesTagsFromXmlAndNotes() throws IOException {
            Path cbz = mockComicInfo("""
                    <Tags>existing-tag</Tags>
                    <Notes>[BookLore:Tags] new-tag, another-tag</Notes>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getTags()).containsExactlyInAnyOrder("existing-tag", "new-tag", "another-tag");
        }

        @Test
        void notesUsedAsDescriptionWhenSummaryMissing() throws IOException {
            Path cbz = mockComicInfo("<Notes>This is a great comic.\n[BookLore:ISBN13] 9780000000000</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getDescription()).isEqualTo("This is a great comic.");
            assertThat(metadata.getIsbn13()).isEqualTo("9780000000000");
        }

        @Test
        void notesNotUsedAsDescriptionWhenSummaryPresent() throws IOException {
            Path cbz = mockComicInfo("""
                    <Summary>Official summary</Summary>
                    <Notes>This is a great comic.\n[BookLore:ISBN13] 9780000000000</Notes>
                    """);

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getDescription()).isEqualTo("Official summary");
        }

        @Test
        void handlesInvalidRatingGracefully() throws IOException {
            Path cbz = mockComicInfo("<Notes>[BookLore:AmazonRating] not-a-number</Notes>");

            BookMetadata metadata = extractor.extractMetadata(cbz);

            assertThat(metadata.getAmazonRating()).isNull();
        }
    }

    @Nested
    class CoverExtraction {

        @Test
        void extractsCoverFromCbzWithImage() throws IOException {
            byte[] expected = createMinimalJpeg(1);
            Path cbz = mockArchiveContents(Map.of(
                    "ComicInfo.xml", wrapInComicInfo("<Title>Test</Title>").getBytes(),
                    "path_001.jpg", expected
            ));

            byte[] actual = extractor.extractCover(cbz);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void returnNullForEmptyCbz() throws IOException {
            Path cbzPath = mockArchiveContents(Map.of(
                    "readme.txt", "no images here".getBytes()
            ));

            byte[] cover = extractor.extractCover(cbzPath);

            assertThat(cover).isNull();
        }

        @Test
        void extractsCoverFromFirstAlphabeticalImage() throws IOException {
            byte[] expected = createMinimalJpeg(2);
            Path cbzPath = mockArchiveContents(Map.of(
                    "page003.jpg", createMinimalJpeg(1),
                    "page001.jpg", expected,
                    "page002.jpg", createMinimalJpeg(3)
            ));

            byte[] actual = extractor.extractCover(cbzPath);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void prefersCoverNamedFileOverAlphabetical() throws IOException {
            byte[] expected = createMinimalJpeg(1);
            Path cbzPath = mockArchiveContents(Map.of(
                    "page001.jpg", createMinimalJpeg(2),
                    "cover.jpg", expected
            ));

            byte[] actual = extractor.extractCover(cbzPath);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void extractsCoverViaFrontCoverPageElement() throws IOException {
            String xml = wrapInComicInfo("""
                      <Title>Test</Title>
                      <Pages>
                        <Page Image="1" ImageFile="page001.jpg"/>
                        <Page Image="2" Type="FrontCover" ImageFile="cover_image.jpg"/>
                        <Page Image="3" ImageFile="page003.jpg"/>
                      </Pages>
                    """);

            byte[] expected = createMinimalJpeg(1);

            Path cbzPath = mockArchiveContents(Map.of(
                    "ComicInfo.xml", xml.getBytes(),
                    "page001.jpg", createMinimalJpeg(2),
                    "cover_image.jpg", expected,
                    "page003.jpg", createMinimalJpeg(3)
            ));

            byte[] actual = extractor.extractCover(cbzPath);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void frontCoverPageByImageIndex() throws IOException {
            String xml = wrapInComicInfo("""
                      <Title>Test</Title>
                      <Pages>
                        <Page Image="0" Type="FrontCover"/>
                      </Pages>
                    """);

            byte[] expected = createMinimalJpeg(1);
            Path cbzPath = mockArchiveContents(Map.of(
                    "ComicInfo.xml", xml.getBytes(),
                    "page001.jpg", expected
            ));

            byte[] actual = extractor.extractCover(cbzPath);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void skipsMacOsxEntries() throws IOException {
            byte[] expected = createMinimalJpeg(1);
            Path cbzPath = mockArchiveContents(Map.of(
                    "__MACOSX/._cover.jpg", createMinimalJpeg(2),
                    "page001.jpg", expected
            ));

            byte[] actual = extractor.extractCover(cbzPath);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void skipsDotFiles() throws IOException {
            byte[] expected = createMinimalJpeg(1);
            Path cbzPath = mockArchiveContents(Map.of(
                    ".hidden.jpg", createMinimalJpeg(2),
                    ".DS_Store", "data".getBytes(),
                    "actual_page.jpg", expected
            ));

            byte[] cover = extractor.extractCover(cbzPath);

            assertThat(cover).isEqualTo(expected);
        }

        @Test
        void returnsPlaceholderForCorruptFile() throws IOException {
            Path path = mockRaisesException();

            byte[] cover = extractor.extractCover(path);

            assertThat(cover).isNull();
        }
    }

    @Nested
    class ComicInfoCaseInsensitive {

        @Test
        void findsComicInfoRegardlessOfCase() throws IOException {
            String xml = wrapInComicInfo("<Title>Case Test</Title>");

            Path cbzPath = mockArchiveContents(Map.of(
                    "COMICINFO.XML", xml.getBytes()
            ));

            BookMetadata metadata = extractor.extractMetadata(cbzPath);

            assertThat(metadata.getTitle()).isEqualTo("Case Test");
        }

        @Test
        void findsComicInfoInSubdirectory() throws IOException {
            String xml = wrapInComicInfo("<Title>Subdir Test</Title>");

            Path cbzPath = mockArchiveContents(Map.of(
                    "metadata/ComicInfo.xml", xml.getBytes()
            ));

            BookMetadata metadata = extractor.extractMetadata(cbzPath);

            assertThat(metadata.getTitle()).isEqualTo("Subdir Test");
        }
    }

    @Nested
    class FullComicInfoIntegration {

        @Test
        void extractsAllFieldsFromRichComicInfo() throws IOException {
            Path cbz = mockComicInfo("""
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
                    """);

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
            byte[] expected = createMinimalJpeg(1);
            Path cbzPath = mockArchiveContents(Map.of(
                    "image.jpg", expected
            ));

            byte[] actual = extractor.extractCover(cbzPath);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void recognizesPngExtension() throws IOException {
            byte[] expected = createMinimalPng();
            Path cbzPath = mockArchiveContents(Map.of(
                    "image.png", expected
            ));

            byte[] actual = extractor.extractCover(cbzPath);

            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class UnknownArchiveType {

        @Test
        void returnsFallbackForNonArchiveFile() throws IOException {
            Path path = mockRaisesException();

            BookMetadata metadata = extractor.extractMetadata(path);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }

        @Test
        void returnsPlaceholderCoverForNonArchiveFile() throws IOException {
            Path path = mockRaisesException();

            byte[] cover = extractor.extractCover(path);

            assertThat(cover).isNull();
        }
    }
}
