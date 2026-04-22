package org.booklore.app.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.app.dto.AppNotebookBookSummary;
import org.booklore.app.dto.AppNotebookEntry;
import org.booklore.app.dto.AppNotebookUpdateRequest;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.model.dto.UpdateAnnotationRequest;
import org.booklore.model.dto.UpdateBookMarkRequest;
import org.booklore.model.dto.UpdateBookNoteV2Request;
import org.booklore.repository.AuthorRepository;
import org.booklore.repository.NotebookEntryRepository;
import org.booklore.service.book.AnnotationService;
import org.booklore.service.book.BookMarkService;
import org.booklore.service.book.BookNoteV2Service;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AppNotebookService {

    private final NotebookEntryRepository notebookEntryRepository;
    private final AuthorRepository authorRepository;
    private final AuthenticationService authenticationService;
    private final AnnotationService annotationService;
    private final BookNoteV2Service bookNoteV2Service;
    private final BookMarkService bookMarkService;

    @Transactional(readOnly = true)
    public AppPageResponse<AppNotebookBookSummary> getBooksWithAnnotations(int page, int size, String search) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Pageable pageable = PageRequest.of(page, size);

        Page<NotebookEntryRepository.BookWithCountProjection> booksPage =
                notebookEntryRepository.findBooksWithAnnotationsPaginated(userId, wrapSearch(search), pageable);

        List<NotebookEntryRepository.BookWithCountProjection> content = booksPage.getContent();
        if (content.isEmpty()) {
            return AppPageResponse.of(List.of(), page, size, 0);
        }

        Set<Long> bookIds = content.stream()
                .map(NotebookEntryRepository.BookWithCountProjection::getBookId)
                .collect(Collectors.toSet());

        Map<Long, List<String>> authorsByBook = authorRepository.findAuthorNamesByBookIds(bookIds)
                .stream()
                .collect(Collectors.groupingBy(
                        AuthorRepository.AuthorBookProjection::getBookId,
                        Collectors.mapping(AuthorRepository.AuthorBookProjection::getAuthorName, Collectors.toList())));

        List<AppNotebookBookSummary> summaries = content.stream()
                .map(p -> AppNotebookBookSummary.builder()
                        .bookId(p.getBookId())
                        .bookTitle(p.getBookTitle())
                        .noteCount(p.getNoteCount())
                        .authors(authorsByBook.getOrDefault(p.getBookId(), List.of()))
                        .coverUpdatedOn(p.getCoverUpdatedOn())
                        .build())
                .toList();

        return AppPageResponse.of(summaries, page, size, booksPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AppPageResponse<AppNotebookEntry> getEntriesForBook(Long bookId, int page, int size,
                                                                      Set<String> types, String search, String sort) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Set<String> entryTypes = (types == null || types.isEmpty())
                ? Set.of("HIGHLIGHT", "NOTE", "BOOKMARK")
                : types;
        Pageable pageable = PageRequest.of(page, size, toSort(sort));

        Page<NotebookEntryRepository.EntryProjection> entriesPage =
                notebookEntryRepository.findEntries(userId, entryTypes, bookId, wrapSearch(search), pageable);

        List<AppNotebookEntry> entries = entriesPage.getContent().stream()
                .map(AppNotebookService::toMobileEntry)
                .toList();

        return AppPageResponse.of(entries, page, size, entriesPage.getTotalElements());
    }

    @Transactional
    public AppNotebookEntry updateEntry(Long entryId, String type, AppNotebookUpdateRequest request) {
        return switch (type.toUpperCase()) {
            case "HIGHLIGHT" -> {
                var updateReq = new UpdateAnnotationRequest();
                if (request.getNote() != null) updateReq.setNote(request.getNote());
                if (request.getColor() != null) updateReq.setColor(request.getColor());
                var result = annotationService.updateAnnotation(entryId, updateReq);
                yield AppNotebookEntry.builder()
                        .id(result.getId())
                        .type("HIGHLIGHT")
                        .bookId(result.getBookId())
                        .text(result.getText())
                        .note(result.getNote())
                        .color(result.getColor())
                        .style(result.getStyle())
                        .chapterTitle(result.getChapterTitle())
                        .createdAt(result.getCreatedAt())
                        .updatedAt(result.getUpdatedAt())
                        .build();
            }
            case "NOTE" -> {
                var updateReq = new UpdateBookNoteV2Request();
                if (request.getNote() != null) updateReq.setNoteContent(request.getNote());
                if (request.getColor() != null) updateReq.setColor(request.getColor());
                var result = bookNoteV2Service.updateNote(entryId, updateReq);
                yield AppNotebookEntry.builder()
                        .id(result.getId())
                        .type("NOTE")
                        .bookId(result.getBookId())
                        .text(result.getSelectedText())
                        .note(result.getNoteContent())
                        .color(result.getColor())
                        .chapterTitle(result.getChapterTitle())
                        .createdAt(result.getCreatedAt())
                        .updatedAt(result.getUpdatedAt())
                        .build();
            }
            case "BOOKMARK" -> {
                var updateReq = new UpdateBookMarkRequest();
                if (request.getNote() != null) updateReq.setNotes(request.getNote());
                if (request.getColor() != null) updateReq.setColor(request.getColor());
                var result = bookMarkService.updateBookmark(entryId, updateReq);
                yield AppNotebookEntry.builder()
                        .id(result.getId())
                        .type("BOOKMARK")
                        .bookId(result.getBookId())
                        .text(result.getTitle())
                        .note(result.getNotes())
                        .color(result.getColor())
                        .createdAt(result.getCreatedAt())
                        .updatedAt(result.getUpdatedAt())
                        .build();
            }
            default -> throw new IllegalArgumentException("Unknown entry type: " + type);
        };
    }

    @Transactional
    public void deleteEntry(Long entryId, String type) {
        switch (type.toUpperCase()) {
            case "HIGHLIGHT" -> annotationService.deleteAnnotation(entryId);
            case "NOTE" -> bookNoteV2Service.deleteNote(entryId);
            case "BOOKMARK" -> bookMarkService.deleteBookmark(entryId);
            default -> throw new IllegalArgumentException("Unknown entry type: " + type);
        }
    }

    private String wrapSearch(String search) {
        if (search == null || search.isBlank()) return null;
        return "%" + escapeLike(search) + "%";
    }

    private static String escapeLike(String input) {
        return input.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Sort toSort(String sort) {
        if ("chapter".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.asc("chapterTitle"), Sort.Order.asc("createdAt"));
        }
        if ("date_asc".equalsIgnoreCase(sort)) {
            return Sort.by("createdAt").ascending();
        }
        return Sort.by("createdAt").descending();
    }

    private static AppNotebookEntry toMobileEntry(NotebookEntryRepository.EntryProjection p) {
        return AppNotebookEntry.builder()
                .id(p.getId())
                .type(p.getType())
                .bookId(p.getBookId())
                .text(p.getText())
                .note(p.getNote())
                .color(p.getColor())
                .style(p.getStyle())
                .chapterTitle(p.getChapterTitle())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
