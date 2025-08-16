package com.adityachandel.booklore.service.watcher;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static com.adityachandel.booklore.model.enums.PermissionType.ADMIN;
import static com.adityachandel.booklore.model.enums.PermissionType.MANIPULATE_LIBRARY;

@Slf4j
@Service
@AllArgsConstructor
public class BookFilePersistenceService {

    private final EntityManager entityManager;
    private final BookRepository bookRepository;
    private final NotificationService notificationService;
    private final BookMapper bookMapper;

    @Transactional
    public void updatePathIfChanged(BookEntity book, LibraryEntity libraryEntity, Path path, String currentHash) {
        LibraryPathEntity newLibraryPath = getLibraryPathEntityForFile(libraryEntity, path.toString());
        newLibraryPath = entityManager.merge(newLibraryPath);

        String newSubPath = FileUtils.getRelativeSubPath(newLibraryPath.getPath(), path);

        boolean pathChanged = !Objects.equals(newSubPath, book.getFileSubPath()) || !Objects.equals(newLibraryPath.getId(), book.getLibraryPath().getId());

        if (pathChanged) {
            book.setLibraryPath(newLibraryPath);
            book.setFileSubPath(newSubPath);
            book.setDeleted(false);
            bookRepository.save(book);
            log.info("[FILE_CREATE] Updated path for existing book with hash '{}': '{}'", currentHash, path);
        } else {
            log.info("[FILE_CREATE] Book with hash '{}' already exists at same path. Skipping update.", currentHash);
        }
        notificationService.sendMessageToPermissions(Topic.BOOK_ADD, bookMapper.toBookWithDescription(book, false), Set.of(ADMIN, MANIPULATE_LIBRARY));
    }

    @Transactional(readOnly = true)
    public Optional<BookEntity> findByHash(String hash) {
        return bookRepository.findByCurrentHash(hash);
    }

    String findMatchingLibraryPath(LibraryEntity libraryEntity, Path filePath) {
        return libraryEntity.getLibraryPaths().stream()
                .map(lp -> Paths.get(lp.getPath()).toAbsolutePath().normalize())
                .filter(base -> filePath.toAbsolutePath().normalize().startsWith(base))
                .map(Path::toString)
                .findFirst()
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException("No matching libraryPath for: " + filePath));
    }

    LibraryPathEntity getLibraryPathEntityForFile(LibraryEntity libraryEntity, String inputPath) {
        Path fullPath = Paths.get(inputPath).toAbsolutePath().normalize();
        return libraryEntity.getLibraryPaths().stream()
                .map(lp -> Map.entry(lp, Paths.get(lp.getPath()).toAbsolutePath().normalize()))
                .filter(entry -> fullPath.startsWith(entry.getValue()))
                .max(Comparator.comparingInt(entry -> entry.getValue().getNameCount()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(inputPath));
    }

    @Transactional
    public int markAllBooksUnderPathAsDeleted(long libraryPathId, String relativeFolderPath) {
        String normalizedPrefix = relativeFolderPath.endsWith("/") ? relativeFolderPath : (relativeFolderPath + "/");

        List<BookEntity> books = bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(libraryPathId, normalizedPrefix);
        books.forEach(book -> {
            book.setDeleted(true);
            book.setDeletedAt(Instant.now());
        });

        bookRepository.saveAll(books);
        return books.size();
    }

    @Transactional(readOnly = true)
    public Optional<BookEntity> findByLibraryPathSubPathAndFileName(long libraryPathId, String fileSubPath, String fileName) {
        return bookRepository.findByLibraryPath_IdAndFileSubPathAndFileName(libraryPathId, fileSubPath, fileName);
    }

    @Transactional
    public void save(BookEntity book) {
        bookRepository.save(book);
    }
}