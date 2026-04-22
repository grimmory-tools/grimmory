package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.BookFileType;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@AllArgsConstructor
public class MetadataExtractorFactory {

    private final EpubMetadataExtractor epubMetadataExtractor;
    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final CbxMetadataExtractor cbxMetadataExtractor;
    private final Fb2MetadataExtractor fb2MetadataExtractor;
    private final MobiMetadataExtractor mobiMetadataExtractor;
    private final Azw3MetadataExtractor azw3MetadataExtractor;
    private final AudiobookMetadataExtractor audiobookMetadataExtractor;

    public BookMetadata extractMetadata(BookFileType bookFileType, File file) {
        return switch (bookFileType) {
            case PDF -> pdfMetadataExtractor.extractMetadata(file);
            case EPUB -> epubMetadataExtractor.extractMetadata(file);
            case CBX -> cbxMetadataExtractor.extractMetadata(file);
            case FB2 -> fb2MetadataExtractor.extractMetadata(file);
            case MOBI -> mobiMetadataExtractor.extractMetadata(file);
            case AZW3 -> azw3MetadataExtractor.extractMetadata(file);
            case AUDIOBOOK -> audiobookMetadataExtractor.extractMetadata(file);
        };
    }

    public BookMetadata extractMetadata(BookFileExtension fileExt, File file) {
        return switch (fileExt) {
            case PDF -> pdfMetadataExtractor.extractMetadata(file);
            case EPUB -> epubMetadataExtractor.extractMetadata(file);
            case CBZ, CBR, CB7 -> cbxMetadataExtractor.extractMetadata(file);
            case FB2 -> fb2MetadataExtractor.extractMetadata(file);
            case MOBI -> mobiMetadataExtractor.extractMetadata(file);
            case AZW3, AZW -> azw3MetadataExtractor.extractMetadata(file);
            case M4B, M4A, MP3, OPUS -> audiobookMetadataExtractor.extractMetadata(file);
        };
    }

    public byte[] extractCover(BookFileExtension fileExt, File file) {
        return switch (fileExt) {
            case EPUB -> epubMetadataExtractor.extractCover(file);
            case PDF -> pdfMetadataExtractor.extractCover(file);
            case CBZ, CBR, CB7 -> cbxMetadataExtractor.extractCover(file);
            case FB2 -> fb2MetadataExtractor.extractCover(file);
            case MOBI -> mobiMetadataExtractor.extractCover(file);
            case AZW3, AZW -> azw3MetadataExtractor.extractCover(file);
            case M4B, M4A, MP3, OPUS -> audiobookMetadataExtractor.extractCover(file);
        };
    }

    public FileMetadataExtractor getExtractor(BookFileType bookFileType) {
        return switch (bookFileType) {
            case PDF -> pdfMetadataExtractor;
            case EPUB -> epubMetadataExtractor;
            case CBX -> cbxMetadataExtractor;
            case FB2 -> fb2MetadataExtractor;
            case MOBI -> mobiMetadataExtractor;
            case AZW3 -> azw3MetadataExtractor;
            case AUDIOBOOK -> audiobookMetadataExtractor;
        };
    }
}
