package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.DuplicateFileInfo;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.FileService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public abstract class AbstractFileProcessor implements BookFileProcessor {

    protected final BookRepository bookRepository;
    protected final BookAdditionalFileRepository bookAdditionalFileRepository;
    protected final BookCreatorService bookCreatorService;
    protected final BookMapper bookMapper;
    protected final MetadataMatchService metadataMatchService;
    protected final FileService fileService;
    @PersistenceContext
    private EntityManager entityManager;


    protected AbstractFileProcessor(BookRepository bookRepository,
                                    BookAdditionalFileRepository bookAdditionalFileRepository,
                                    BookCreatorService bookCreatorService,
                                    BookMapper bookMapper,
                                    FileService fileService,
                                    MetadataMatchService metadataMatchService) {
        this.bookRepository = bookRepository;
        this.bookAdditionalFileRepository = bookAdditionalFileRepository;
        this.bookCreatorService = bookCreatorService;
        this.bookMapper = bookMapper;
        this.metadataMatchService = metadataMatchService;
        this.fileService = fileService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public FileProcessResult processFile(LibraryFile libraryFile) {
        Path path = libraryFile.getFullPath();
        String fileName = path.getFileName().toString();
        String hash = FileFingerprint.generateHash(path);

        Optional<Book> duplicate = fileService.checkForDuplicateAndUpdateMetadataIfNeeded(libraryFile, hash, bookRepository, bookAdditionalFileRepository, bookMapper);

        if (duplicate.isPresent()) {
            return handleDuplicate(duplicate.get(), libraryFile, hash);
        }

        Long libraryId = libraryFile.getLibraryEntity().getId();
        return bookRepository.findBookByFileNameAndLibraryId(fileName, libraryId)
                .map(bookMapper::toBook)
                .map(b -> new FileProcessResult(b, FileProcessStatus.DUPLICATE, createDuplicateInfo(b, libraryFile, hash)))
                .orElseGet(() -> {
                    Book book = createAndMapBook(libraryFile, hash);
                    return new FileProcessResult(book, FileProcessStatus.NEW, null);
                });
    }

    private FileProcessResult handleDuplicate(Book bookDto, LibraryFile libraryFile, String hash) {
        return bookRepository.findById(bookDto.getId())
                .map(entity -> {
                    boolean sameHash = Objects.equals(entity.getCurrentHash(), hash);
                    boolean sameFileName = Objects.equals(entity.getFileName(), libraryFile.getFileName());
                    boolean sameSubPath = Objects.equals(entity.getFileSubPath(), libraryFile.getFileSubPath());
                    boolean sameLibraryPath = Objects.equals(entity.getLibraryPath(), libraryFile.getLibraryPathEntity());

                    if (sameHash && sameFileName && sameSubPath && sameLibraryPath) {
                        return new FileProcessResult(
                                bookDto,
                                FileProcessStatus.DUPLICATE,
                                createDuplicateInfo(bookDto, libraryFile, hash)
                        );
                    }

                    boolean folderChanged = !sameSubPath;
                    boolean updated = false;

                    if (!sameSubPath) {
                        entity.setFileSubPath(libraryFile.getFileSubPath());
                        updated = true;
                    }

                    if (!sameFileName) {
                        entity.setFileName(libraryFile.getFileName());
                        updated = true;
                    }

                    if (!sameLibraryPath) {
                        entity.setLibraryPath(libraryFile.getLibraryPathEntity());
                        updated = true;
                    }

                    entity.setCurrentHash(hash);

                    /*if (folderChanged) {
                        log.info("Duplicate file found in different folder: bookId={} oldSubPath='{}' newSubPath='{}'",
                                entity.getId(),
                                bookDto.getFileSubPath(),
                                libraryFile.getFileSubPath());
                    }*/

                    DuplicateFileInfo dupeInfo = createDuplicateInfo(bookMapper.toBook(entity), libraryFile, hash);

                    if (updated) {
                        /*log.info("Duplicate file updated: bookId={} fileName='{}' libraryId={} subPath='{}'",
                                entity.getId(),
                                entity.getFileName(),
                                entity.getLibraryPath().getLibrary().getId(),
                                entity.getFileSubPath());*/
                        entityManager.flush();
                        entityManager.detach(entity);
                        return new FileProcessResult(bookMapper.toBook(entity), FileProcessStatus.UPDATED, dupeInfo);
                    } else {
                        entityManager.detach(entity);
                        return new FileProcessResult(bookMapper.toBook(entity), FileProcessStatus.DUPLICATE, dupeInfo);
                    }
                })
                .orElse(new FileProcessResult(bookDto, FileProcessStatus.DUPLICATE, null));
    }

    private DuplicateFileInfo createDuplicateInfo(Book book, LibraryFile libraryFile, String hash) {
        return new DuplicateFileInfo(
                book.getId(),
                libraryFile.getFileName(),
                libraryFile.getFullPath().toString(),
                hash
        );
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