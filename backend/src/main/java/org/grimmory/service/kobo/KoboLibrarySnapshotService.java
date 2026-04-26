package org.grimmory.service.kobo;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.mapper.BookEntityToKoboSnapshotBookMapper;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.entity.*;
import org.grimmory.model.enums.ShelfType;
import org.grimmory.repository.KoboDeletedBookProgressRepository;
import org.grimmory.repository.KoboLibrarySnapshotRepository;
import org.grimmory.repository.KoboSnapshotBookRepository;
import org.grimmory.repository.ShelfRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class KoboLibrarySnapshotService {

    private final KoboLibrarySnapshotRepository koboLibrarySnapshotRepository;
    private final KoboSnapshotBookRepository koboSnapshotBookRepository;
    private final ShelfRepository shelfRepository;
    private final BookEntityToKoboSnapshotBookMapper mapper;
    private final KoboDeletedBookProgressRepository koboDeletedBookProgressRepository;
    private final KoboCompatibilityService koboCompatibilityService;
    private final AuthenticationService authenticationService;

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

        List<KoboSnapshotBookEntity> books = mapBooksToKoboSnapshotBook(getKoboShelf(userId), snapshot);
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

    private ShelfEntity getKoboShelf(Long userId) {
        return shelfRepository
                .findByUserIdAndName(userId, ShelfType.KOBO.getName())
                .orElseThrow(() -> new NoSuchElementException(
                        String.format("Shelf '%s' not found for user %d", ShelfType.KOBO.getName(), userId)
                ));
    }

    private List<KoboSnapshotBookEntity> mapBooksToKoboSnapshotBook(ShelfEntity shelf, KoboLibrarySnapshotEntity snapshot) {
        Long userId = snapshot.getUserId();

        return shelf.getBookEntities().stream()
                .filter(book -> isBookOwnedByUser(book, userId))
                .filter(koboCompatibilityService::isBookSupportedForKobo)
                .map(book -> {
                    KoboSnapshotBookEntity snapshotBook = mapper.toKoboSnapshotBook(book);
                    snapshotBook.setSnapshot(snapshot);
                    snapshotBook.setFileHash(book.getPrimaryBookFile().getCurrentHash());
                    snapshotBook.setMetadataUpdatedAt(book.getMetadataUpdatedAt());
                    return snapshotBook;
                })
                .collect(Collectors.toList());
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