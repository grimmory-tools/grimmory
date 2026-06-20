package org.booklore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.request.HighlightPayload;
import org.booklore.model.dto.CreateAnnotationRequest;
import org.booklore.model.dto.CreateBookNoteV2Request;
import org.booklore.service.book.AnnotationService;
import org.booklore.service.book.BookNoteV2Service;
import org.booklore.config.security.service.AuthenticationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/highlights")
@RequiredArgsConstructor
public class HighlightController {

    private final AnnotationService annotationService;
    private final BookNoteV2Service bookNoteV2Service;
    private final AuthenticationService authenticationService;

    @PostMapping
    public ResponseEntity<Void> receiveHighlight(@RequestBody HighlightPayload payload) {
        log.info("KOReader Sync -> Cor: {} | Capítulo: {} | Nota: {}", payload.getColor(), payload.getChapterTitle(), payload.getNote());
        
        try {
            String cfi = payload.getCfi() != null && !payload.getCfi().isEmpty() ? payload.getCfi() : "epubcfi(/0)";
            String rawColor = payload.getColor();
            String color = (rawColor != null && rawColor.matches("^#[0-9A-Fa-f]{6}$")) ? rawColor : "#FFE58F";
            String note = payload.getNote();

            boolean hasNote = note != null && !note.trim().isEmpty();

            if (hasNote) {
                // 1. Tem anotação textual: Salvar em BookNoteV2
                CreateBookNoteV2Request request = new CreateBookNoteV2Request();
                request.setBookId(payload.getBookId());
                request.setCfi(cfi);
                request.setSelectedText(payload.getText());
                request.setChapterTitle(payload.getChapterTitle());
                request.setNoteContent(note);
                request.setColor(color);

                bookNoteV2Service.createNote(request);
                log.info("Registado como Nota (book_notes_v2)");
            } else {
                // 2. Apenas sublinhado: Salvar em Annotation
                CreateAnnotationRequest request = new CreateAnnotationRequest();
                request.setBookId(payload.getBookId());
                request.setCfi(cfi);
                request.setText(payload.getText());
                request.setChapterTitle(payload.getChapterTitle());
                request.setNote(null);
                request.setColor(color);
                request.setStyle("highlight");

                annotationService.createAnnotation(request);
                log.info("Registado como Destaque (annotations)");
            }

        } catch (Exception e) {
            log.error("Error saving highlight", e);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteHighlight(@RequestParam Long bookId, @RequestParam String cfi) {
        log.info("Deleting highlight/note | Book: {} | CFI: {}", bookId, cfi);
        try {
            Long userId = authenticationService.getAuthenticatedUser().getId();
            
            // O KOReader não envia flag indicando se era nota ou destaque ao eliminar.
            // Para garantir a higienização, disparamos a exclusão em ambas as tabelas.
            try {
                annotationService.deleteByCfiAndBookIdAndUserId(cfi, bookId, userId);
            } catch (Exception e) {
                log.warn("Nenhum destaque encontrado em annotations para este CFI.");
            }
            
            try {
                bookNoteV2Service.deleteByCfiAndBookIdAndUserId(cfi, bookId, userId);
            } catch (Exception e) {
                log.warn("Nenhuma nota encontrada em book_notes_v2 para este CFI.");
            }

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting highlight", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}