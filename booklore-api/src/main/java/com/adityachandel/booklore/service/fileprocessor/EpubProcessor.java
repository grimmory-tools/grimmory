package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.FileUtils;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class EpubProcessor implements FileProcessor {

    private final BookRepository bookRepository;
    private final BookCreatorService bookCreatorService;
    private final BookMapper bookMapper;
    private final FileProcessingUtils fileProcessingUtils;
    private final BookMetadataRepository bookMetadataRepository;
    private final MetadataMatchService metadataMatchService;
    private final EpubMetadataExtractor epubMetadataExtractor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Book processFile(LibraryFile libraryFile, boolean forceProcess) {
        File bookFile = new File(libraryFile.getFileName());
        String fileName = bookFile.getName();
        if (!forceProcess) {
            Optional<BookEntity> bookOptional = bookRepository.findBookByFileNameAndLibraryId(fileName, libraryFile.getLibraryEntity().getId());
            return bookOptional
                    .map(bookMapper::toBook)
                    .orElseGet(() -> processNewFile(libraryFile));
        } else {
            return processNewFile(libraryFile);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Book processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB);
        setBookMetadata(bookEntity);
        Float score = metadataMatchService.calculateMatchScore(bookEntity);
        bookEntity.setMetadataMatchScore(score);
        if (generateCover(bookEntity)) {
            fileProcessingUtils.setBookCoverPath(bookEntity.getId(), bookEntity.getMetadata());
        }
        bookCreatorService.saveConnections(bookEntity);
        bookRepository.save(bookEntity);
        bookRepository.flush();
        return bookMapper.toBook(bookEntity);
    }

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
            boolean saved = saveCoverImage(coverImage, bookEntity.getId());
            bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
            bookMetadataRepository.save(bookEntity.getMetadata());
            return saved;
        } catch (Exception e) {
            log.error("Error generating cover for epub file {}, error: {}", bookEntity.getFileName(), e.getMessage(), e);
        }
        return false;
    }

    private void setBookMetadata(BookEntity bookEntity) {
        File bookFile = new File(bookEntity.getFullFilePath().toUri());

        BookMetadata epubMetadata = epubMetadataExtractor.extractMetadata(bookFile);
        if (epubMetadata == null) return;

        BookMetadataEntity bookMetadata = bookEntity.getMetadata();
        bookMetadata.setTitle(truncate(epubMetadata.getTitle(), 1000));
        bookMetadata.setSubtitle(truncate(epubMetadata.getSubtitle(), 1000));
        bookMetadata.setDescription(truncate(epubMetadata.getDescription(), 2000));
        bookMetadata.setPublisher(truncate(epubMetadata.getPublisher(), 1000));
        bookMetadata.setPublishedDate(epubMetadata.getPublishedDate());
        bookMetadata.setSeriesName(truncate(epubMetadata.getSeriesName(), 1000));
        bookMetadata.setSeriesNumber(epubMetadata.getSeriesNumber());
        bookMetadata.setSeriesTotal(epubMetadata.getSeriesTotal());
        bookMetadata.setIsbn13(truncate(epubMetadata.getIsbn13(), 64));
        bookMetadata.setIsbn10(truncate(epubMetadata.getIsbn10(), 64));
        bookMetadata.setPageCount(epubMetadata.getPageCount());

        String lang = epubMetadata.getLanguage();
        bookMetadata.setLanguage(truncate((lang == null || lang.equalsIgnoreCase("UND")) ? "en" : lang, 1000));

        bookMetadata.setAsin(truncate(epubMetadata.getAsin(), 20));
        bookMetadata.setPersonalRating(epubMetadata.getPersonalRating());
        bookMetadata.setAmazonRating(epubMetadata.getAmazonRating());
        bookMetadata.setAmazonReviewCount(epubMetadata.getAmazonReviewCount());
        bookMetadata.setGoodreadsId(truncate(epubMetadata.getGoodreadsId(), 100));
        bookMetadata.setGoodreadsRating(epubMetadata.getGoodreadsRating());
        bookMetadata.setGoodreadsReviewCount(epubMetadata.getGoodreadsReviewCount());
        bookMetadata.setHardcoverId(truncate(epubMetadata.getHardcoverId(), 100));
        bookMetadata.setHardcoverRating(epubMetadata.getHardcoverRating());
        bookMetadata.setHardcoverReviewCount(epubMetadata.getHardcoverReviewCount());
        bookMetadata.setGoogleId(truncate(epubMetadata.getGoogleId(), 100));

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

    private String truncate(String input, int maxLength) {
        if (input == null) return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}