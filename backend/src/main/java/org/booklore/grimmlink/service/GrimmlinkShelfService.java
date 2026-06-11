package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.grimmlink.dto.GrimmlinkBookSummary;
import org.booklore.grimmlink.dto.GrimmlinkShelfRemovalResponse;
import org.booklore.grimmlink.dto.GrimmlinkShelfSummary;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.MagicShelfEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrimmlinkShelfService {

    private final GrimmlinkAuthService authService;
    private final GrimmlinkBookService bookService;
    private final BookRepository bookRepository;
    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final MagicShelfBookService magicShelfBookService;

    @Transactional(readOnly = true)
    public List<GrimmlinkShelfSummary> listShelves(String typeFilter) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        String normalizedType = normalizeShelfTypeOrNull(typeFilter);
        List<GrimmlinkShelfSummary> summaries = new ArrayList<>();
        if (normalizedType == null || "regular".equals(normalizedType)) {
            summaries.addAll(shelfRepository.findByUserIdOrPublicShelfTrue(reader.getId()).stream()
                    .filter(shelf -> canReadShelf(reader, shelf))
                    .map(shelf -> GrimmlinkShelfSummary.builder()
                            .id(shelf.getId())
                            .name(shelf.getName())
                            .type("regular")
                            .visibility(shelf.isPublic() ? "public" : "personal")
                            .bookCount(shelf.getBookCount())
                            .build())
                    .toList());
        }
        if (normalizedType == null || "magic".equals(normalizedType)) {
            Map<Long, MagicShelfEntity> shelvesById = new LinkedHashMap<>();
            magicShelfRepository.findAllByUserId(reader.getId())
                    .forEach(shelf -> shelvesById.put(shelf.getId(), shelf));
            magicShelfRepository.findAllByIsPublicIsTrue()
                    .forEach(shelf -> shelvesById.putIfAbsent(shelf.getId(), shelf));
            summaries.addAll(shelvesById.values().stream()
                    .filter(shelf -> canReadMagicShelf(reader, shelf))
                    .map(shelf -> GrimmlinkShelfSummary.builder()
                            .id(shelf.getId())
                            .name(shelf.getName())
                            .type("magic")
                            .visibility(shelf.isPublic() ? "public" : "personal")
                            .bookCount(countMagicShelfBooks(reader, shelf.getId()))
                            .description("Rule-based Magic Shelf")
                            .build())
                    .toList());
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public List<GrimmlinkBookSummary> listShelfBooks(
            String shelfType,
            Long shelfId,
            Integer limit,
            Integer offset,
            String cursor) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        List<GrimmlinkBookSummary> books;
        if ("magic".equals(normalizeShelfType(shelfType))) {
            List<Long> bookIds = magicShelfBookService.getBookIdsByMagicShelfId(reader.getId(), shelfId);
            if (bookIds == null || bookIds.isEmpty()) {
                return List.of();
            }
            List<BookEntity> bookEntities = bookRepository
                    .findAllForSummaryByIds(bookIds.stream().distinct().toList());
            Map<Long, BookEntity> byId = bookEntities.stream()
                    .collect(Collectors.toMap(
                            BookEntity::getId,
                            book -> book,
                            (left, right) -> left));
            books = bookIds.stream()
                    .distinct()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .filter(book -> bookService.canAccessBook(reader, book))
                    .map(this::toBookSummary)
                    .toList();
        } else {
            ShelfEntity shelf = shelfRepository.findByIdWithUser(shelfId)
                    .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
            if (!canReadShelf(reader, shelf)) {
                throw ApiError.FORBIDDEN.createException(
                        "Shelf is not accessible to the authenticated user");
            }
            books = bookRepository.findAllWithMetadataByShelfId(shelfId).stream()
                    .filter(book -> bookService.canAccessBook(reader, book))
                    .map(this::toBookSummary)
                    .toList();
        }
        return applyShelfPagination(books, limit, offset, cursor);
    }

    @Transactional
    public GrimmlinkShelfRemovalResponse removeBookFromShelf(
            String shelfType,
            Long shelfId,
            Long bookId) {
        BookLoreUserEntity reader = authService.requireCurrentReader(true);
        String normalizedType = normalizeShelfType(shelfType);
        if ("magic".equals(normalizedType)) {
            return GrimmlinkShelfRemovalResponse.builder()
                    .shelfId(shelfId)
                    .bookId(bookId)
                    .shelfType("magic")
                    .removed(false)
                    .status("unsupported")
                    .message("Magic Shelf is rule-based and cannot be manually removed from")
                    .build();
        }
        ShelfEntity shelf = shelfRepository.findByIdWithUser(shelfId)
                .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
        if (!canModifyShelf(reader, shelf)) {
            throw ApiError.FORBIDDEN.createException(
                    "Shelf membership can only be modified by the shelf owner or an admin");
        }
        BookEntity book = bookService.loadAccessibleBookById(reader, bookId);
        boolean removed = book.getShelves().removeIf(
                existingShelf -> existingShelf.getId().equals(shelfId));
        if (removed) {
            bookRepository.save(book);
        }
        return GrimmlinkShelfRemovalResponse.builder()
                .shelfId(shelfId)
                .bookId(bookId)
                .shelfType("regular")
                .removed(removed)
                .status(removed ? "removed" : "noop")
                .message(removed
                        ? "Shelf membership removed"
                        : "Book is not currently in this shelf")
                .build();
    }

    private List<GrimmlinkBookSummary> applyShelfPagination(
            List<GrimmlinkBookSummary> books,
            Integer limit,
            Integer offset,
            String cursor) {
        int safeOffset = 0;
        if (cursor != null && !cursor.isBlank()) {
            try {
                Long cursorBookId = Long.parseLong(cursor.trim());
                for (int i = 0; i < books.size(); i++) {
                    if (books.get(i).getBookId() != null
                            && books.get(i).getBookId().equals(cursorBookId)) {
                        safeOffset = i + 1;
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                log.debug("Invalid shelf cursor value: {}", cursor);
            }
        }
        if (offset != null && offset >= 0) {
            safeOffset = offset;
        }
        if (safeOffset >= books.size()) {
            return List.of();
        }
        int safeLimit = limit != null && limit > 0 ? Math.min(limit, 100) : 100;
        int toIndex = Math.min(safeOffset + safeLimit, books.size());
        return books.subList(safeOffset, toIndex);
    }

    private GrimmlinkBookSummary toBookSummary(BookEntity book) {
        BookFileEntity primaryFile = bookService.resolvePrimaryFile(book);
        return GrimmlinkBookSummary.builder()
                .bookId(book.getId())
                .bookFileId(primaryFile != null ? primaryFile.getId() : null)
                .title(book.getMetadata() != null ? book.getMetadata().getTitle() : null)
                .author(resolveAuthor(book))
                .fileName(primaryFile != null ? primaryFile.getFileName() : null)
                .originalFileName(primaryFile != null ? primaryFile.getFileName() : null)
                .extension(resolveExtension(primaryFile != null ? primaryFile.getFileName() : null))
                .fileFormat(primaryFile != null && primaryFile.getBookType() != null
                        ? primaryFile.getBookType().name()
                        : null)
                .fileSizeKb(primaryFile != null ? primaryFile.getFileSizeKb() : null)
                .fileSize(primaryFile != null && primaryFile.getFileSizeKb() != null
                        ? primaryFile.getFileSizeKb() * 1024
                        : null)
                .bookHash(bookService.resolveHash(primaryFile))
                .seriesName(book.getMetadata() != null ? book.getMetadata().getSeriesName() : null)
                .seriesNumber(book.getMetadata() != null ? book.getMetadata().getSeriesNumber() : null)
                .build();
    }

    private String resolveExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT)
                : null;
    }

    private String resolveAuthor(BookEntity book) {
        if (book.getMetadata() == null || book.getMetadata().getAuthors() == null) {
            return null;
        }
        return book.getMetadata().getAuthors().stream()
                .map(AuthorEntity::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
    }

    private Integer countMagicShelfBooks(BookLoreUserEntity reader, Long shelfId) {
        try {
            List<Long> ids = magicShelfBookService.getBookIdsByMagicShelfId(reader.getId(), shelfId);
            return ids != null ? ids.size() : 0;
        } catch (Exception ex) {
            log.debug("Unable to resolve magic shelf count for shelf {}: {}", shelfId, ex.getMessage());
            return null;
        }
    }

    private String normalizeShelfTypeOrNull(String shelfType) {
        return bookService.trimToNull(shelfType) == null ? null : normalizeShelfType(shelfType);
    }

    private String normalizeShelfType(String shelfType) {
        String normalized = shelfType == null
                ? "regular"
                : shelfType.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "regular";
        }
        if (!"regular".equals(normalized) && !"magic".equals(normalized)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Unsupported shelf type: " + shelfType);
        }
        return normalized;
    }

    private boolean canReadShelf(BookLoreUserEntity reader, ShelfEntity shelf) {
        return bookService.isAdmin(reader)
                || shelf.isPublic()
                || shelf.getUser().getId().equals(reader.getId());
    }

    private boolean canModifyShelf(BookLoreUserEntity reader, ShelfEntity shelf) {
        return bookService.isAdmin(reader) || shelf.getUser().getId().equals(reader.getId());
    }

    private boolean canReadMagicShelf(BookLoreUserEntity reader, MagicShelfEntity shelf) {
        return bookService.isAdmin(reader)
                || shelf.isPublic()
                || shelf.getUserId().equals(reader.getId());
    }
}
