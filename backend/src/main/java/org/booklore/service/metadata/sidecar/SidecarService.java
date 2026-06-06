package org.booklore.service.metadata.sidecar;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.sidecar.SidecarMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.model.enums.SidecarSyncStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.metadata.BookMetadataUpdater;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class SidecarService {

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final SidecarMetadataReader sidecarReader;
    private final SidecarMetadataWriter sidecarWriter;
    private final SidecarMetadataMapper sidecarMapper;
    private final BookMetadataUpdater bookMetadataUpdater;

    public Optional<SidecarMetadata> getSidecarContent(Long bookId) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        Path bookPath = book.getFullFilePath();
        if (bookPath == null) {
            return Optional.empty();
        }

        return sidecarReader.readSidecarMetadata(bookPath);
    }

    public SidecarSyncStatus getSyncStatus(Long bookId) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        return sidecarReader.getSyncStatus(book);
    }

    @Transactional
    public void exportToSidecar(Long bookId) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        sidecarWriter.writeSidecarMetadata(book);
    }

    @Transactional
    public void importFromSidecar(Long bookId) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        Path bookPath = book.getFullFilePath();
        if (bookPath == null) {
            throw ApiError.FILE_NOT_FOUND.createException("Book has no file path");
        }

        Optional<SidecarMetadata> sidecarOpt = sidecarReader.readSidecarMetadata(bookPath);
        if (sidecarOpt.isEmpty()) {
            throw ApiError.FILE_NOT_FOUND.createException("No sidecar file found for book");
        }

        SidecarMetadata sidecar = sidecarOpt.get();
        BookMetadata bookMetadata = sidecarMapper.toBookMetadata(sidecar);

        if (bookMetadata != null) {
            MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                    .metadata(bookMetadata)
                    .build();

            MetadataUpdateContext context = MetadataUpdateContext.builder()
                    .bookEntity(book)
                    .metadataUpdateWrapper(wrapper)
                    .updateThumbnail(false)
                    .replaceMode(MetadataReplaceMode.REPLACE_WHEN_PROVIDED)
                    .build();

            bookMetadataUpdater.setBookMetadata(context);
        }

        byte[] coverBytes = sidecarReader.readSidecarCover(bookPath);
        if (coverBytes != null) {
            log.info("Sidecar cover found for book ID {} - cover import is a separate operation", bookId);
        }
    }

    @Transactional
    public int bulkExport(Long libraryId) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        List<BookEntity> books = bookRepository.findAllByLibraryIdWithFiles(libraryId);
        int exported = 0;

        for (BookEntity book : books) {
            try {
                sidecarWriter.writeSidecarMetadata(book);
                exported++;
            } catch (Exception e) {
                log.warn("Failed to export sidecar for book ID {}: {}", book.getId(), e.getMessage());
            }
        }

        log.info("Bulk exported {} sidecar files for library {}", exported, library.getName());
        return exported;
    }

    @Transactional
    public int bulkImport(Long libraryId) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        List<BookEntity> books = bookRepository.findAllByLibraryIdWithFiles(libraryId);
        int imported = 0;

        for (BookEntity book : books) {
            try {
                Path bookPath = book.getFullFilePath();
                if (bookPath == null) {
                    continue;
                }

                Optional<SidecarMetadata> sidecarOpt = sidecarReader.readSidecarMetadata(bookPath);
                if (sidecarOpt.isEmpty()) {
                    continue;
                }

                SidecarMetadata sidecar = sidecarOpt.get();
                BookMetadata bookMetadata = sidecarMapper.toBookMetadata(sidecar);

                if (bookMetadata != null) {
                    MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                            .metadata(bookMetadata)
                            .build();

                    MetadataUpdateContext context = MetadataUpdateContext.builder()
                            .bookEntity(book)
                            .metadataUpdateWrapper(wrapper)
                            .updateThumbnail(false)
                            .replaceMode(MetadataReplaceMode.REPLACE_WHEN_PROVIDED)
                            .build();

                    bookMetadataUpdater.setBookMetadata(context);
                    imported++;
                }
            } catch (Exception e) {
                log.warn("Failed to import sidecar for book ID {}: {}", book.getId(), e.getMessage());
            }
        }

        log.info("Bulk imported {} sidecar files for library {}", imported, library.getName());
        return imported;
    }
}
