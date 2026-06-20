package org.booklore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.request.HighlightPayload;
import org.booklore.model.dto.CreateAnnotationRequest;
import org.booklore.service.book.AnnotationService;
import org.booklore.config.security.service.AuthenticationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/highlights")
@RequiredArgsConstructor
public class HighlightController {

    private final AnnotationService annotationService; // Alterado para AnnotationService
    private final AuthenticationService authenticationService;

    @PostMapping
    public ResponseEntity<Void> receiveHighlight(@RequestBody HighlightPayload payload) {
        log.info("KOReader Sync -> Cor: {} | Capítulo: {} | Nota: {}", payload.getColor(), payload.getChapterTitle(), payload.getNote());
        
        try {
            CreateAnnotationRequest request = new CreateAnnotationRequest(); // Usando DTO correto
            request.setBookId(payload.getBookId());
            request.setCfi(payload.getCfi() != null && !payload.getCfi().isEmpty() ? payload.getCfi() : "epubcfi(/0)");
            request.setText(payload.getText());
            request.setChapterTitle(payload.getChapterTitle());

            String note = payload.getNote();
            request.setNote(note == null || note.trim().isEmpty() ? null : note); // Retornamos o null autêntico

            String rawColor = payload.getColor();
            if (rawColor != null && rawColor.matches("^#[0-9A-Fa-f]{6}$")) {
                request.setColor(rawColor);
            } else {
                request.setColor("#FFE58F"); 
            }

            request.setStyle("highlight"); // Obrigatório na nova tabela

            annotationService.createAnnotation(request); // Chamada ao serviço de anotações
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
            annotationService.deleteByCfiAndBookIdAndUserId(cfi, bookId, userId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting highlight", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}