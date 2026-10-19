package org.booklore.service.kobo;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookEntityToKoboSnapshotBookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.*;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.KoboDeletedBookProgressRepository;
import org.booklore.repository.KoboLibrarySnapshotRepository;
import org.booklore.repository.KoboSnapshotBookRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.service.opds.MagicShelfBookService;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@AllArgsConstructor
@Service
public class KoboLibrarySnapshotService {

    public static final int MAX_MAGIC_SHELF_BOOKS_FOR_KOBO = 250;

    private final KoboLibrarySnapshotRepository koboLibrarySnapshotRepository;
    private final KoboSnapshotBookRepository koboSnapshotBookRepository;
    private final ShelfRepository shelfRepository;
    private final BookEntityToKoboSnapshotBookMapper mapper;
    private final KoboDeletedBookProgressRepository koboDeletedBookProgressRepository;
    private final KoboCompatibilityService koboCompatibilityService;
    private final AuthenticationService authenticationService;
    private final MagicShelfRepository magicShelfRepository;
    private final MagicShelfBookService magicShelfBookService;
    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public Optional<KoboLibrarySnapshotEntity> findByIdAndUserId(String id, Long userId) {
        return koboLibrarySnapshotRepository.findByIdAndUserId(id, userId);
    }

    @Transactional
    public KoboLibrarySnapshotEntity create(Long userId) {
        KoboLibrarySnapshotEntity snapshot = KoboLibrarySnapshotEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .build();

        List<KoboSnapshotBookEntity> books = mapBooksToKoboSnapshotBook(resolveSyncedBooks(userId), snapshot);
        snapshot.setBooks(books);

        return koboLibrarySnapshotRepository.save(snapshot);
    }

    @Transactional
    public Page<KoboSnapshotBookEntity> getUnsyncedBooks(String snapshotId, Pageable pageable) {
        Page<KoboSnapshotBookEntity> page = koboSnapshotBookRepository.findBySnapshot_IdAndSyncedFalse(snapshotId, pageable);
        List<Long> bookIds = page.getContent().stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();
        if (!bookIds.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(snapshotId, bookIds);
        }
        return page;
    }

    @Transactional
    public void updateSyncedStatusForExistingBooks(String previousSnapshotId, String currentSnapshotId) {
        List<KoboSnapshotBookEntity> list = koboSnapshotBookRepository.findUnchangedBooksBetweenSnapshots(previousSnapshotId, currentSnapshotId);
        List<Long> unchangedBooks = list.stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();

        if (!unchangedBooks.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(currentSnapshotId, unchangedBooks);
        }
    }

    @Transactional
    public Page<KoboSnapshotBookEntity> getNewlyAddedBooks(String previousSnapshotId, String currentSnapshotId, Pageable pageable, Long userId) {
        Page<KoboSnapshotBookEntity> page = koboSnapshotBookRepository.findNewlyAddedBooks(previousSnapshotId, currentSnapshotId, true, pageable);
        List<Long> newlyAddedBookIds = page.getContent().stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();

        if (!newlyAddedBookIds.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(currentSnapshotId, newlyAddedBookIds);
        }

        return page;
    }

    @Transactional
    public Page<KoboSnapshotBookEntity> getRemovedBooks(String previousSnapshotId, String currentSnapshotId, Long userId, Pageable pageable) {
        Page<KoboSnapshotBookEntity> page = koboSnapshotBookRepository.findRemovedBooks(previousSnapshotId, currentSnapshotId, pageable);

        List<Long> bookIds = page.getContent().stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();

        if (!bookIds.isEmpty()) {
            List<KoboDeletedBookProgressEntity> progressEntities = bookIds.stream()
                    .map(bookId -> KoboDeletedBookProgressEntity.builder()
                            .bookIdSynced(bookId)
                            .snapshotId(currentSnapshotId)
                            .userId(userId)
                            .build())
                    .toList();

            koboDeletedBookProgressRepository.saveAll(progressEntities);
        }
        return page;
    }

    @Transactional
    public Page<KoboSnapshotBookEntity> getChangedBooks(String previousSnapshotId, String currentSnapshotId, Pageable pageable) {
        Page<KoboSnapshotBookEntity> page = koboSnapshotBookRepository.findChangedBooks(previousSnapshotId, currentSnapshotId, pageable);
        List<Long> changedBookIds = page.getContent().stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();

        if (!changedBookIds.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(currentSnapshotId, changedBookIds);
        }

        return page;
    }

    /**
     * Books a user's Kobo should receive: the legacy "Kobo" shelf plus every shelf and
     * magic shelf flagged with "Sync to Kobo".
     */
    @Transactional(readOnly = true)
    public Set<BookEntity> resolveSyncedBooks(Long userId) {
        Map<Long, BookEntity> books = new LinkedHashMap<>();

        shelfRepository.findByUserIdAndName(userId, ShelfType.KOBO.getName())
                .ifPresent(shelf -> shelf.getBookEntities().forEach(book -> books.putIfAbsent(book.getId(), book)));

        shelfRepository.findByUserIdAndKoboSyncTrue(userId)
                .forEach(shelf -> shelf.getBookEntities().forEach(book -> books.putIfAbsent(book.getId(), book)));

        Set<Long> magicShelfBookIds = new LinkedHashSet<>();
        for (MagicShelfEntity magicShelf : magicShelfRepository.findAllByUserIdAndKoboSyncTrue(userId)) {
            try {
                magicShelfBookIds.addAll(magicShelfBookService.getBookIdsByMagicShelfId(userId, magicShelf.getId(), MAX_MAGIC_SHELF_BOOKS_FOR_KOBO));
            } catch (Exception e) {
                log.warn("Skipping magic shelf '{}' during Kobo sync: {}", magicShelf.getName(), e.getMessage());
            }
        }
        magicShelfBookIds.removeAll(books.keySet());
        if (!magicShelfBookIds.isEmpty()) {
            bookRepository.findAllById(magicShelfBookIds).forEach(book -> books.putIfAbsent(book.getId(), book));
        }

        return new LinkedHashSet<>(books.values());
    }

    private List<KoboSnapshotBookEntity> mapBooksToKoboSnapshotBook(Set<BookEntity> syncedBooks, KoboLibrarySnapshotEntity snapshot) {
        Long userId = snapshot.getUserId();

        return syncedBooks.stream()
                .filter(book -> isBookOwnedByUser(book, userId))
                .filter(koboCompatibilityService::isBookSupportedForKobo)
                .map(book -> {
                    KoboSnapshotBookEntity snapshotBook = mapper.toKoboSnapshotBook(book);
                    snapshotBook.setSnapshot(snapshot);
                    snapshotBook.setFileHash(book.getPrimaryBookFile().getCurrentHash());
                    snapshotBook.setMetadataUpdatedAt(book.getMetadataUpdatedAt());
                    return snapshotBook;
                })
                .toList();
    }

    private boolean isBookOwnedByUser(BookEntity book, Long userId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (user.getPermissions().isAdmin()) {
            return true;
        }
        return book.getLibrary()
                .getUsers()
                .stream()
                .map(BookLoreUserEntity::getId)
                .anyMatch(id -> Objects.equals(id, userId));
    }

    public void deleteById(String id) {
        koboLibrarySnapshotRepository.deleteById(id);
    }

}