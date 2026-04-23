package org.booklore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.app.dto.AppBookContextResponse;
import org.booklore.app.service.AppBookService;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.repository.CbxViewerPreferencesRepository;
import org.booklore.repository.EbookViewerPreferenceRepository;
import org.booklore.repository.NewPdfViewerPreferencesRepository;
import org.booklore.repository.PdfViewerPreferencesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/books")
@Tag(name = "App Books")
@Slf4j
public class AppBookContextController {

    private final AppBookService mobileBookService;
    private final AuthenticationService authenticationService;
    private final PdfViewerPreferencesRepository pdfPrefsRepo;
    private final NewPdfViewerPreferencesRepository newPdfPrefsRepo;
    private final EbookViewerPreferenceRepository ebookPrefsRepo;
    private final CbxViewerPreferencesRepository cbxPrefsRepo;
    private final BookMapper bookMapper;

    @Operation(
            summary = "Get consolidated book context",
            description = "Retrieve book details and reader preferences in a single request.",
            operationId = "appGetBookContext"
    )
    @GetMapping("/{bookId}/context")
    public ResponseEntity<AppBookContextResponse> getBookContext(@PathVariable Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        var detail = mobileBookService.getBookDetail(bookId);

        var response = AppBookContextResponse.builder()
                .book(detail)
                .pdfSettings(pdfPrefsRepo.findByBookIdAndUserId(bookId, userId)
                        .map(e -> bookMapper.toPdfViewerPreferences(e)).orElse(null))
                .newPdfSettings(newPdfPrefsRepo.findByBookIdAndUserId(bookId, userId)
                        .map(e -> bookMapper.toNewPdfViewerPreferences(e)).orElse(null))
                .ebookSettings(ebookPrefsRepo.findByBookIdAndUserId(bookId, userId)
                        .map(e -> bookMapper.toEbookViewerPreferences(e)).orElse(null))
                .cbxSettings(cbxPrefsRepo.findByBookIdAndUserId(bookId, userId)
                        .map(e -> bookMapper.toCbxViewerPreferences(e)).orElse(null))
                .build();

        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=60")
                .body(response);
    }
}
