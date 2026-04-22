package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataExtractorFactoryTest {

    @Mock private EpubMetadataExtractor epubMetadataExtractor;
    @Mock private PdfMetadataExtractor pdfMetadataExtractor;
    @Mock private CbxMetadataExtractor cbxMetadataExtractor;
    @Mock private Fb2MetadataExtractor fb2MetadataExtractor;
    @Mock private MobiMetadataExtractor mobiMetadataExtractor;
    @Mock private Azw3MetadataExtractor azw3MetadataExtractor;
    @Mock private AudiobookMetadataExtractor audiobookMetadataExtractor;

    private MetadataExtractorFactory factory;
    private final File dummyFile = new File("test.bin");
    private final BookMetadata dummyMetadata = BookMetadata.builder().title("Test").build();

    @BeforeEach
    void setUp() {
        factory = new MetadataExtractorFactory(
                epubMetadataExtractor, pdfMetadataExtractor, cbxMetadataExtractor,
                fb2MetadataExtractor, mobiMetadataExtractor, azw3MetadataExtractor,
                audiobookMetadataExtractor
        );
    }

    @Nested
    class ExtractMetadataByBookFileType {

        @Test
        void routesToPdf() {
            when(pdfMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileType.PDF, dummyFile)).isEqualTo(dummyMetadata);
            verify(pdfMetadataExtractor).extractMetadata(dummyFile);
        }

        @Test
        void routesToEpub() {
            when(epubMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileType.EPUB, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesToCbx() {
            when(cbxMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileType.CBX, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesToFb2() {
            when(fb2MetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileType.FB2, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesToMobi() {
            when(mobiMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileType.MOBI, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesToAzw3() {
            when(azw3MetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileType.AZW3, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesToAudiobook() {
            when(audiobookMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileType.AUDIOBOOK, dummyFile)).isEqualTo(dummyMetadata);
        }

        @ParameterizedTest
        @EnumSource(BookFileType.class)
        void allFileTypesHandled(BookFileType type) {
            factory.extractMetadata(type, dummyFile);
        }
    }

    @Nested
    class ExtractMetadataByBookFileExtension {

        @Test
        void routesPdf() {
            when(pdfMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.PDF, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesEpub() {
            when(epubMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.EPUB, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesCbz() {
            when(cbxMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.CBZ, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesCbr() {
            when(cbxMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.CBR, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesCb7() {
            when(cbxMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.CB7, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesFb2() {
            when(fb2MetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.FB2, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesMobi() {
            when(mobiMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.MOBI, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesAzw3() {
            when(azw3MetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.AZW3, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesAzw() {
            when(azw3MetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.AZW, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesM4b() {
            when(audiobookMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.M4B, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesM4a() {
            when(audiobookMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.M4A, dummyFile)).isEqualTo(dummyMetadata);
        }

        @Test
        void routesMp3() {
            when(audiobookMetadataExtractor.extractMetadata(dummyFile)).thenReturn(dummyMetadata);
            assertThat(factory.extractMetadata(BookFileExtension.MP3, dummyFile)).isEqualTo(dummyMetadata);
        }

        @ParameterizedTest
        @EnumSource(BookFileExtension.class)
        void allExtensionsHandled(BookFileExtension ext) {
            factory.extractMetadata(ext, dummyFile);
        }
    }

    @Nested
    class ExtractCoverByBookFileExtension {

        @Test
        void routesEpubCover() {
            byte[] coverData = {1, 2, 3};
            when(epubMetadataExtractor.extractCover(dummyFile)).thenReturn(coverData);
            assertThat(factory.extractCover(BookFileExtension.EPUB, dummyFile)).isEqualTo(coverData);
        }

        @Test
        void routesPdfCover() {
            byte[] coverData = {4, 5, 6};
            when(pdfMetadataExtractor.extractCover(dummyFile)).thenReturn(coverData);
            assertThat(factory.extractCover(BookFileExtension.PDF, dummyFile)).isEqualTo(coverData);
        }

        @Test
        void routesCbzCover() {
            when(cbxMetadataExtractor.extractCover(dummyFile)).thenReturn(new byte[]{1});
            assertThat(factory.extractCover(BookFileExtension.CBZ, dummyFile)).isEqualTo(new byte[]{1});
        }

        @Test
        void routesFb2Cover() {
            when(fb2MetadataExtractor.extractCover(dummyFile)).thenReturn(new byte[]{1});
            assertThat(factory.extractCover(BookFileExtension.FB2, dummyFile)).isEqualTo(new byte[]{1});
        }

        @Test
        void routesAudiobookCover() {
            when(audiobookMetadataExtractor.extractCover(dummyFile)).thenReturn(new byte[]{1});
            assertThat(factory.extractCover(BookFileExtension.M4B, dummyFile)).isEqualTo(new byte[]{1});
        }

        @ParameterizedTest
        @EnumSource(BookFileExtension.class)
        void allExtensionsCoverHandled(BookFileExtension ext) {
            factory.extractCover(ext, dummyFile);
        }
    }

    @Nested
    class GetExtractor {

        @Test
        void returnsPdfExtractor() {
            assertThat(factory.getExtractor(BookFileType.PDF)).isSameAs(pdfMetadataExtractor);
        }

        @Test
        void returnsEpubExtractor() {
            assertThat(factory.getExtractor(BookFileType.EPUB)).isSameAs(epubMetadataExtractor);
        }

        @Test
        void returnsCbxExtractor() {
            assertThat(factory.getExtractor(BookFileType.CBX)).isSameAs(cbxMetadataExtractor);
        }

        @Test
        void returnsFb2Extractor() {
            assertThat(factory.getExtractor(BookFileType.FB2)).isSameAs(fb2MetadataExtractor);
        }

        @Test
        void returnsMobiExtractor() {
            assertThat(factory.getExtractor(BookFileType.MOBI)).isSameAs(mobiMetadataExtractor);
        }

        @Test
        void returnsAzw3Extractor() {
            assertThat(factory.getExtractor(BookFileType.AZW3)).isSameAs(azw3MetadataExtractor);
        }

        @Test
        void returnsAudiobookExtractor() {
            assertThat(factory.getExtractor(BookFileType.AUDIOBOOK)).isSameAs(audiobookMetadataExtractor);
        }

        @ParameterizedTest
        @EnumSource(BookFileType.class)
        void allFileTypesReturnNonNull(BookFileType type) {
            assertThat(factory.getExtractor(type)).isNotNull();
        }
    }
}
