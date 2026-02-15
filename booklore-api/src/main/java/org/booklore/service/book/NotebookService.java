package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.NotebookBookOption;
import org.booklore.model.dto.NotebookEntry;
import org.booklore.repository.NotebookEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotebookService {

    private static final int EXPORT_LIMIT = 50_000;
    private static final int BOOK_OPTIONS_LIMIT = 50;

    private final NotebookEntryRepository repository;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public Page<NotebookEntry> getNotebookEntries(int page, int size, Set<String> types, Long bookId,
                                                  String search, String sort) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        return repository.findEntries(userId, types, bookId, wrapSearch(search), pageable)
                .map(NotebookService::toDto);
    }

    @Transactional(readOnly = true)
    public List<NotebookEntry> getAllNotebookEntries(Set<String> types, Long bookId, String search, String sort) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Pageable pageable = PageRequest.of(0, EXPORT_LIMIT, toSort(sort));
        return repository.findEntries(userId, types, bookId, wrapSearch(search), pageable)
                .map(NotebookService::toDto)
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<NotebookBookOption> getBooksWithAnnotations(String search) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return repository.findBooksWithAnnotations(userId, wrapSearch(search), Pageable.ofSize(BOOK_OPTIONS_LIMIT))
                .stream()
                .map(p -> new NotebookBookOption(p.getBookId(), p.getBookTitle()))
                .toList();
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
        return "asc".equalsIgnoreCase(sort)
                ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();
    }

    private static NotebookEntry toDto(NotebookEntryRepository.EntryProjection p) {
        return NotebookEntry.builder()
                .id(p.getId())
                .type(p.getType())
                .bookId(p.getBookId())
                .bookTitle(p.getBookTitle())
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
