package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookRestorationService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreDeletedBooks(List<LibraryFile> libraryFiles) {
        if (libraryFiles.isEmpty()) return;

        LibraryEntity libraryEntity = libraryFiles.getFirst().getLibraryEntity();
        Set<Path> currentPaths = libraryFiles.stream()
                .map(LibraryFile::getFullPath)
                .collect(Collectors.toSet());

        List<BookEntity> toRestore = libraryEntity.getBookEntities().stream()
                .filter(book -> Boolean.TRUE.equals(book.getDeleted()))
                .filter(book -> currentPaths.contains(book.getFullFilePath()))
                .collect(Collectors.toList());

        if (toRestore.isEmpty()) return;

        toRestore.forEach(book -> {
            book.setDeleted(false);
            book.setDeletedAt(null);
            book.setAddedOn(Instant.now());
            notificationService.sendMessage(Topic.BOOK_ADD, bookMapper.toBookWithDescription(book, false));
        });
        bookRepository.saveAll(toRestore);

        List<Long> restoredIds = toRestore.stream()
                .map(BookEntity::getId)
                .toList();

        log.info("Restored {} books in library: {}", restoredIds.size(), libraryEntity.getName());
    }
}
