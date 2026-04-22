package org.booklore.service.book;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.response.DetachBookFileResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.BookCoverService;
import org.booklore.service.progress.ReadingProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class BookFileDetachmentService {

    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final AuthenticationService authenticationService;
    private final ReadingProgressService readingProgressService;
    private final BookMapper bookMapper;
    private final BookService bookService;
    private final AuditService auditService;
    private final BookCoverService bookCoverService;

    @Transactional
    public DetachBookFileResponse detachBookFile(Long bookId, Long fileId, boolean copyMetadata) {
        BookEntity sourceBook = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        BookFileEntity targetFile = sourceBook.getBookFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> ApiError.GENERIC_BAD_REQUEST.createException("File not found on this book"));

        if (sourceBook.getBookFiles().size() <= 1) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Cannot detach the only file from a book");
        }

        if (targetFile.isBookFormat()) {
            long bookFormatCount = sourceBook.getBookFiles().stream()
                    .filter(BookFileEntity::isBookFormat)
                    .count();
            if (bookFormatCount == 1) {
                boolean hasSupplementaryOnly = sourceBook.getBookFiles().stream()
                        .anyMatch(f -> !f.isBookFormat());
                if (hasSupplementaryOnly) {
                    throw ApiError.GENERIC_BAD_REQUEST.createException(
                            "Cannot detach the only book format file. The book would be left with only supplementary files.");
                }
            }
        }

        Path fileDiskPath = targetFile.getFullFilePath();
        LibraryPathEntity newLibraryPath = resolveLibraryPath(sourceBook, fileDiskPath);
        String newFileSubPath = computeFileSubPath(newLibraryPath, fileDiskPath);

        BookEntity newBook = BookEntity.builder()
                .library(sourceBook.getLibrary())
                .libraryPath(newLibraryPath)
                .addedOn(Instant.now())
                .build();

        BookMetadataEntity newMetadata;
        if (copyMetadata) {
            newMetadata = copyMetadataFrom(sourceBook.getMetadata());
        } else {
            newMetadata = BookMetadataEntity.builder().build();
            String title = deriveTitle(targetFile.getFileName());
            newMetadata.setTitle(title);
        }
        newMetadata.setBook(newBook);
        newBook.setMetadata(newMetadata);

        sourceBook.getBookFiles().remove(targetFile);
        targetFile.setFileSubPath(newFileSubPath);
        targetFile.setBook(newBook);

        newBook = bookRepository.saveAndFlush(newBook);

        try {
            bookCoverService.regenerateCover(newBook.getId());
        } catch (Exception e) {
            log.warn("Failed to generate cover for detached book {}: {}", newBook.getId(), e.getMessage());
        }

        auditService.log(AuditAction.BOOK_FILE_DETACHED, "Book", bookId,
                "Detached file '" + targetFile.getFileName() + "' from book " + bookId + " to new book " + newBook.getId());

        Book sourceBookDto = getUpdatedBook(sourceBook.getId());
        Book newBookDto = getUpdatedBook(newBook.getId());

        return new DetachBookFileResponse(sourceBookDto, newBookDto);
    }

    private BookMetadataEntity copyMetadataFrom(BookMetadataEntity source) {
        if (source == null) {
            return BookMetadataEntity.builder().build();
        }

        BookMetadataEntity copy = BookMetadataEntity.builder().build();
        copy.setTitle(source.getTitle());
        copy.setSubtitle(source.getSubtitle());
        copy.setPublisher(source.getPublisher());
        copy.setPublishedDate(source.getPublishedDate());
        copy.setDescription(source.getDescription());
        copy.setSeriesName(source.getSeriesName());
        copy.setSeriesNumber(source.getSeriesNumber());
        copy.setSeriesTotal(source.getSeriesTotal());
        copy.setIsbn13(source.getIsbn13());
        copy.setIsbn10(source.getIsbn10());
        copy.setPageCount(source.getPageCount());
        copy.setLanguage(source.getLanguage());
        copy.setRating(source.getRating());
        copy.setReviewCount(source.getReviewCount());
        copy.setAmazonRating(source.getAmazonRating());
        copy.setAmazonReviewCount(source.getAmazonReviewCount());
        copy.setGoodreadsRating(source.getGoodreadsRating());
        copy.setGoodreadsReviewCount(source.getGoodreadsReviewCount());
        copy.setHardcoverRating(source.getHardcoverRating());
        copy.setHardcoverReviewCount(source.getHardcoverReviewCount());
        copy.setLubimyczytacRating(source.getLubimyczytacRating());
        copy.setRanobedbRating(source.getRanobedbRating());
        copy.setAudibleRating(source.getAudibleRating());
        copy.setAudibleReviewCount(source.getAudibleReviewCount());
        copy.setAsin(source.getAsin());
        copy.setGoodreadsId(source.getGoodreadsId());
        copy.setHardcoverId(source.getHardcoverId());
        copy.setHardcoverBookId(source.getHardcoverBookId());
        copy.setGoogleId(source.getGoogleId());
        copy.setComicvineId(source.getComicvineId());
        copy.setLubimyczytacId(source.getLubimyczytacId());
        copy.setRanobedbId(source.getRanobedbId());
        copy.setAudibleId(source.getAudibleId());
        copy.setNarrator(source.getNarrator());
        copy.setAbridged(source.getAbridged());
        copy.setAgeRating(source.getAgeRating());
        copy.setContentRating(source.getContentRating());

        if (source.getAuthors() != null) {
            copy.setAuthors(new ArrayList<>(source.getAuthors()));
        }
        if (source.getCategories() != null) {
            copy.setCategories(new HashSet<>(source.getCategories()));
        }
        if (source.getMoods() != null) {
            copy.setMoods(new HashSet<>(source.getMoods()));
        }
        if (source.getTags() != null) {
            copy.setTags(new HashSet<>(source.getTags()));
        }

        return copy;
    }

    private String deriveTitle(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Untitled";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private LibraryPathEntity resolveLibraryPath(BookEntity sourceBook, Path fileDiskPath) {
        Path normalizedFilePath = fileDiskPath.toAbsolutePath().normalize();
        return sourceBook.getLibrary().getLibraryPaths().stream()
                .map(lp -> Map.entry(lp, Paths.get(lp.getPath()).toAbsolutePath().normalize()))
                .filter(entry -> normalizedFilePath.startsWith(entry.getValue()))
                .max(Comparator.comparingInt(entry -> entry.getValue().getNameCount()))
                .map(Map.Entry::getKey)
                .orElse(sourceBook.getLibraryPath());
    }

    private String computeFileSubPath(LibraryPathEntity libraryPath, Path fileDiskPath) {
        Path libraryRoot = Paths.get(libraryPath.getPath()).toAbsolutePath().normalize();
        Path fileDir = fileDiskPath.toAbsolutePath().normalize().getParent();
        if (fileDir == null || fileDir.equals(libraryRoot)) {
            return "";
        }
        return libraryRoot.relativize(fileDir).toString();
    }

    private Book getUpdatedBook(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookEntity refreshedBook = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        UserBookProgressEntity userProgress = userBookProgressRepository.findByUserIdAndBookId(user.getId(), bookId)
                .orElse(new UserBookProgressEntity());
        UserBookFileProgressEntity fileProgress = readingProgressService
                .fetchUserFileProgress(user.getId(), Set.of(bookId))
                .get(bookId);

        Book book = bookMapper.toBook(refreshedBook);
        book.setShelves(bookService.filterShelvesByUserId(book.getShelves(), user.getId()));
        readingProgressService.enrichBookWithProgress(book, userProgress, fileProgress);

        return book;
    }
}
