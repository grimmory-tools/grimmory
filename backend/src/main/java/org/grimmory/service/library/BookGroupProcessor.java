package org.grimmory.service.library;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.exception.ApiError;
import org.grimmory.model.FileProcessResult;
import org.grimmory.model.dto.Book;
import org.grimmory.model.dto.settings.LibraryFile;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookFileEntity;
import org.grimmory.model.entity.LibraryEntity;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.repository.BookAdditionalFileRepository;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.LibraryRepository;
import org.grimmory.service.event.BookAddedEvent;
import org.grimmory.service.file.FileFingerprint;
import org.grimmory.service.fileprocessor.BookFileProcessor;
import org.grimmory.service.fileprocessor.BookFileProcessorRegistry;
import org.grimmory.util.FileUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Transactional processor for book file groups.
 * Each process() call runs in its own REQUIRES_NEW transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookGroupProcessor {

    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final BookFileProcessorRegistry processorRegistry;
    private final BookCoverGenerator bookCoverGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(List<LibraryFile> group, long libraryId) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        group.forEach(lf -> lf.setLibraryEntity(library));

        Optional<LibraryFile> primaryFile = findBestPrimaryFile(group, library);
        if (primaryFile.isEmpty()) {
            log.warn("No suitable book file found in group");
            return;
        }

        LibraryFile primary = primaryFile.get();
        log.info("Processing file: {}", primary.getFileName());

        BookFileType type = primary.getBookFileType();
        if (type == null) {
            log.warn("Unsupported file type for file: {}", primary.getFileName());
            return;
        }

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);
        FileProcessResult result = processor.processFile(primary);

        if (result == null || result.getBook() == null) {
            log.warn("Failed to process primary file: {}", primary.getFileName());
            return;
        }

        Book book = result.getBook();
        long bookId = book.getId();

        List<LibraryFile> additionalFiles = group.stream()
                .filter(f -> !f.equals(primary))
                .toList();

        if (!additionalFiles.isEmpty()) {
            BookEntity bookEntity = bookRepository.getReferenceById(bookId);
            for (LibraryFile additionalFile : additionalFiles) {
                createAdditionalBookFile(bookEntity, additionalFile);
            }
        }

        eventPublisher.publishEvent(new BookAddedEvent(book));
    }

    private Optional<LibraryFile> findBestPrimaryFile(List<LibraryFile> group, LibraryEntity library) {
        List<BookFileType> formatPriority = library.getFormatPriority();
        return group.stream()
                .filter(f -> f.getBookFileType() != null)
                .min(Comparator.comparingInt((LibraryFile f) -> {
                    BookFileType bookFileType = f.getBookFileType();
                    if (formatPriority != null && !formatPriority.isEmpty()) {
                        int index = formatPriority.indexOf(bookFileType);
                        return index >= 0 ? index : Integer.MAX_VALUE;
                    }
                    return bookFileType.ordinal();
                }).thenComparing(LibraryFile::getFileName));
    }

    private void createAdditionalBookFile(BookEntity bookEntity, LibraryFile file) {
        Optional<BookFileEntity> existing = bookAdditionalFileRepository
                .findByLibraryPath_IdAndFileSubPathAndFileName(
                        file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());

        if (existing.isPresent()) {
            log.debug("Additional file already exists: {}", file.getFileName());
            return;
        }

        String hash;
        Long fileSizeKb;
        if (file.isFolderBased()) {
            hash = FileFingerprint.generateFolderHash(file.getFullPath());
            fileSizeKb = FileUtils.getFolderSizeInKb(file.getFullPath());
        } else {
            hash = FileFingerprint.generateHash(file.getFullPath());
            fileSizeKb = FileUtils.getFileSizeInKb(file.getFullPath());
        }

        BookFileEntity additionalFile = BookFileEntity.builder()
                .book(bookEntity)
                .fileName(file.getFileName())
                .fileSubPath(file.getFileSubPath())
                .isBookFormat(true)
                .folderBased(file.isFolderBased())
                .bookType(file.getBookFileType())
                .fileSizeKb(fileSizeKb)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();

        try {
            bookAdditionalFileRepository.save(additionalFile);
            String primaryFileName = bookEntity.getPrimaryBookFile() != null ? bookEntity.getPrimaryBookFile().getFileName() : "unknown";
            log.info("Attached additional format {} to book: {}", file.getFileName(), primaryFileName);
            bookCoverGenerator.generateCoverFromAdditionalFile(bookEntity, file);
        } catch (Exception e) {
            log.error("Error creating additional file {}: {}", file.getFileName(), e.getMessage());
        }
    }
}
