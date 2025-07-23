package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
import com.adityachandel.booklore.util.FileUtils;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.service.fileprocessor.FileProcessingUtils.truncate;

@Slf4j
@Service
public class EpubProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final EpubMetadataExtractor epubMetadataExtractor;
    private final BookMetadataRepository bookMetadataRepository;

    public EpubProcessor(BookRepository bookRepository,
                         BookCreatorService bookCreatorService,
                         BookMapper bookMapper,
                         FileProcessingUtils fileProcessingUtils,
                         BookMetadataRepository bookMetadataRepository,
                         MetadataMatchService metadataMatchService,
                         EpubMetadataExtractor epubMetadataExtractor) {
        super(bookRepository, bookCreatorService, bookMapper, fileProcessingUtils, bookMetadataRepository, metadataMatchService);
        this.epubMetadataExtractor = epubMetadataExtractor;
        this.bookMetadataRepository = bookMetadataRepository;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB);
        setBookMetadata(bookEntity);
        if (generateCover(bookEntity)) {
            fileProcessingUtils.setBookCoverPath(bookEntity.getId(), bookEntity.getMetadata());
        }
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        try {
            File epubFile = new File(FileUtils.getBookFullPath(bookEntity));
            io.documentnode.epub4j.domain.Book epub = new EpubReader().readEpub(new FileInputStream(epubFile));
            Resource coverImage = epub.getCoverImage();

            if (coverImage == null) {
                for (Resource res : epub.getResources().getAll()) {
                    String id = res.getId();
                    String href = res.getHref();
                    if ((id != null && id.toLowerCase().contains("cover")) ||
                            (href != null && href.toLowerCase().contains("cover"))) {
                        if (res.getMediaType() != null && res.getMediaType().getName().startsWith("image")) {
                            coverImage = res;
                            break;
                        }
                    }
                }
            }

            if (coverImage == null) {
                log.warn("No cover image found in EPUB '{}'", bookEntity.getFileName());
                return false;
            }

            boolean saved = saveCoverImage(coverImage, bookEntity.getId());
            bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
            bookMetadataRepository.save(bookEntity.getMetadata());
            return saved;

        } catch (Exception e) {
            log.error("Error generating cover for EPUB '{}': {}", bookEntity.getFileName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.EPUB);
    }

    private void setBookMetadata(BookEntity bookEntity) {
        File bookFile = new File(bookEntity.getFullFilePath().toUri());
        BookMetadata epubMetadata = epubMetadataExtractor.extractMetadata(bookFile);
        if (epubMetadata == null) return;

        BookMetadataEntity metadata = bookEntity.getMetadata();

        metadata.setTitle(truncate(epubMetadata.getTitle(), 1000));
        metadata.setSubtitle(truncate(epubMetadata.getSubtitle(), 1000));
        metadata.setDescription(truncate(epubMetadata.getDescription(), 2000));
        metadata.setPublisher(truncate(epubMetadata.getPublisher(), 1000));
        metadata.setPublishedDate(epubMetadata.getPublishedDate());
        metadata.setSeriesName(truncate(epubMetadata.getSeriesName(), 1000));
        metadata.setSeriesNumber(epubMetadata.getSeriesNumber());
        metadata.setSeriesTotal(epubMetadata.getSeriesTotal());
        metadata.setIsbn13(truncate(epubMetadata.getIsbn13(), 64));
        metadata.setIsbn10(truncate(epubMetadata.getIsbn10(), 64));
        metadata.setPageCount(epubMetadata.getPageCount());

        String lang = epubMetadata.getLanguage();
        metadata.setLanguage(truncate((lang == null || lang.equalsIgnoreCase("UND")) ? "en" : lang, 1000));

        metadata.setAsin(truncate(epubMetadata.getAsin(), 20));
        metadata.setPersonalRating(epubMetadata.getPersonalRating());
        metadata.setAmazonRating(epubMetadata.getAmazonRating());
        metadata.setAmazonReviewCount(epubMetadata.getAmazonReviewCount());
        metadata.setGoodreadsId(truncate(epubMetadata.getGoodreadsId(), 100));
        metadata.setGoodreadsRating(epubMetadata.getGoodreadsRating());
        metadata.setGoodreadsReviewCount(epubMetadata.getGoodreadsReviewCount());
        metadata.setHardcoverId(truncate(epubMetadata.getHardcoverId(), 100));
        metadata.setHardcoverRating(epubMetadata.getHardcoverRating());
        metadata.setHardcoverReviewCount(epubMetadata.getHardcoverReviewCount());
        metadata.setGoogleId(truncate(epubMetadata.getGoogleId(), 100));

        bookCreatorService.addAuthorsToBook(epubMetadata.getAuthors(), bookEntity);

        if (epubMetadata.getCategories() != null) {
            Set<String> validSubjects = epubMetadata.getCategories().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                    .collect(Collectors.toSet());
            bookCreatorService.addCategoriesToBook(validSubjects, bookEntity);
        }
    }

    private boolean saveCoverImage(Resource coverImage, long bookId) throws IOException {
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(coverImage.getData()));
        return fileProcessingUtils.saveCoverImage(originalImage, bookId);
    }
}