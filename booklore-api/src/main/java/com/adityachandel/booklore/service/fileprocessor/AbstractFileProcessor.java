package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public abstract class AbstractFileProcessor implements BookFileProcessor {

    protected final BookRepository bookRepository;
    protected final BookAdditionalFileRepository bookAdditionalFileRepository;
    protected final BookCreatorService bookCreatorService;
    protected final BookMapper bookMapper;
    protected final MetadataMatchService metadataMatchService;
    protected final FileService fileService;

    protected AbstractFileProcessor(BookRepository bookRepository, BookAdditionalFileRepository bookAdditionalFileRepository, BookCreatorService bookCreatorService, BookMapper bookMapper, FileService fileService, MetadataMatchService metadataMatchService) {
        this.bookRepository = bookRepository;
        this.bookAdditionalFileRepository = bookAdditionalFileRepository;
        this.bookCreatorService = bookCreatorService;
        this.bookMapper = bookMapper;
        this.metadataMatchService = metadataMatchService;
        this.fileService = fileService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Book processFile(LibraryFile libraryFile) {
        Path path = libraryFile.getFullPath();
        String fileName = path.getFileName().toString();
        String hash = FileFingerprint.generateHash(path);
        Optional<Book> duplicate = fileService.checkForDuplicateAndUpdateMetadataIfNeeded(libraryFile, hash, bookRepository, bookAdditionalFileRepository, bookMapper);
        if (duplicate.isPresent()) {
            return handleDuplicate(duplicate.get(), libraryFile);
        }

        Long libraryId = libraryFile.getLibraryEntity().getId();
        return bookRepository.findBookByFileNameAndLibraryId(fileName, libraryId)
                .map(bookMapper::toBook)
                .orElseGet(() -> createAndMapBook(libraryFile, hash));
    }

    private Book handleDuplicate(Book bookDto, LibraryFile libraryFile) {
        bookRepository.findById(bookDto.getId())
                .ifPresent(entity -> {
                    entity.setFileSubPath(libraryFile.getFileSubPath());
                    entity.setFileName(libraryFile.getFileName());
                    entity.setLibraryPath(libraryFile.getLibraryPathEntity());
                    log.info("Duplicate file handled: bookId={} fileName='{}' libraryId={} subPath='{}'",
                            entity.getId(),
                            libraryFile.getFileName(),
                            libraryFile.getLibraryEntity().getId(),
                            libraryFile.getFileSubPath());
                });
        return bookDto;
    }

    private Book createAndMapBook(LibraryFile libraryFile, String hash) {
        BookEntity entity = processNewFile(libraryFile);
        entity.setCurrentHash(hash);
        entity.setMetadataMatchScore(metadataMatchService.calculateMatchScore(entity));
        bookCreatorService.saveConnections(entity);
        return bookMapper.toBook(entity);
    }

    protected abstract BookEntity processNewFile(LibraryFile libraryFile);
}
