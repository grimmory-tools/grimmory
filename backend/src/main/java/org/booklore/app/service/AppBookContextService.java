package org.booklore.app.service;

import lombok.RequiredArgsConstructor;
import org.booklore.app.dto.AppBookContextResponse;
import org.booklore.mapper.BookMapper;
import org.booklore.repository.CbxViewerPreferencesRepository;
import org.booklore.repository.EbookViewerPreferenceRepository;
import org.booklore.repository.NewPdfViewerPreferencesRepository;
import org.booklore.repository.PdfViewerPreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppBookContextService {

    private final PdfViewerPreferencesRepository pdfPrefsRepo;
    private final NewPdfViewerPreferencesRepository newPdfPrefsRepo;
    private final EbookViewerPreferenceRepository ebookPrefsRepo;
    private final CbxViewerPreferencesRepository cbxPrefsRepo;
    private final BookMapper bookMapper;

    @Transactional(readOnly = true)
    public AppBookContextResponse getBookContext(Long bookId, Long userId) {
        return AppBookContextResponse.builder()
                .pdfSettings(pdfPrefsRepo.findByBookIdAndUserId(bookId, userId)
                        .map(bookMapper::toPdfViewerPreferences)
                        .orElse(null))
                .newPdfSettings(newPdfPrefsRepo.findByBookIdAndUserId(bookId, userId)
                        .map(bookMapper::toNewPdfViewerPreferences)
                        .orElse(null))
                .ebookSettings(ebookPrefsRepo.findByBookIdAndUserId(bookId, userId)
                        .map(bookMapper::toEbookViewerPreferences)
                        .orElse(null))
                .cbxSettings(cbxPrefsRepo.findByBookIdAndUserId(bookId, userId)
                        .map(bookMapper::toCbxViewerPreferences)
                        .orElse(null))
                .build();
    }
}
