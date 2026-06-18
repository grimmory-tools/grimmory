package org.booklore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.request.HighlightPayload;
import org.booklore.model.dto.CreateBookNoteV2Request;
import org.booklore.service.book.BookNoteV2Service;
import org.booklore.config.security.service.AuthenticationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/v1/highlights")
@RequiredArgsConstructor
public class HighlightController {

    private final BookNoteV2Service bookNoteV2Service;
    private final AuthenticationService authenticationService;

    @PostMapping
    public ResponseEntity<Void> receiveHighlight(@RequestBody HighlightPayload payload) {
        try {
            CreateBookNoteV2Request request = new CreateBookNoteV2Request();
            request.setBookId(payload.getBookId());
            request.setCfi(payload.getCfi() != null && !payload.getCfi().isEmpty() ? payload.getCfi() : "epubcfi(/0)");
            request.setSelectedText(payload.getText());

            String note = payload.getNote();
            request.setNoteContent(note == null || note.trim().isEmpty() ? " " : note);
            request.setColor("#FFE58F"); 

            bookNoteV2Service.createNote(request);
        } catch (Exception e) {
            log.error("Error saving highlight", e);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteHighlight(@RequestParam Long bookId, @RequestParam String cfi) {
        log.info("Deleting highlight | Book: {} | CFI: {}", bookId, cfi);
        try {
            Long userId = authenticationService.getAuthenticatedUser().getId();
            bookNoteV2Service.deleteByCfiAndBookIdAndUserId(cfi, bookId, userId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting highlight", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
