package org.booklore.grimmlink.facade;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.grimmlink.repository.GrimmlinkMetadataItemRepository;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.*;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.koreader.KoreaderService;
import org.booklore.service.opds.MagicShelfBookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrimmlinkFacadeReadingSessionTest {

    @Mock private KoreaderService koreaderService;
    @Mock private UserRepository userRepository;
    @Mock private BookRepository bookRepository;
    @Mock private BookFileRepository bookFileRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private UserBookFileProgressRepository userBookFileProgressRepository;
    @Mock private ReadingSessionRepository readingSessionRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private MagicShelfRepository magicShelfRepository;
    @Mock private BookDownloadService bookDownloadService;
    @Mock private MagicShelfBookService magicShelfBookService;
    @Mock private GrimmlinkMetadataItemRepository metadataItemRepository;
    @Mock private BookMapper bookMapper;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private GrimmlinkFacade grimmlinkFacade;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void recordReadingSession_persistsGrimmlinkSpecificFieldsForSinglePayloads() {
        BookLoreUserEntity reader = new BookLoreUserEntity();
        reader.setId(7L);
        LibraryEntity library = new LibraryEntity();
        library.setId(11L);
        library.setFormatPriority(List.of(BookFileType.EPUB));
        reader.setLibraries(Set.of(library));

        BookEntity book = new BookEntity();
        book.setId(99L);
        book.setLibrary(library);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setId(5L);
        primaryFile.setBook(book);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setCurrentHash("hash-123");
        book.setBookFiles(List.of(primaryFile));

        KoreaderUserDetails principal = new KoreaderUserDetails(
                "grimmlink-user",
                "secret",
                true,
                true,
                7L,
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        when(userRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(reader));
        when(bookRepository.findByIdWithBookFiles(99L)).thenReturn(Optional.of(book));
        when(readingSessionRepository.save(org.mockito.ArgumentMatchers.any(ReadingSessionEntity.class)))
                .thenAnswer(invocation -> {
                    ReadingSessionEntity session = invocation.getArgument(0);
                    session.setId(501L);
                    return session;
                });

        ReadingSessionRequest request = new ReadingSessionRequest();
        request.setBookId(99L);
        request.setBookType(BookFileType.EPUB);
        request.setBookHash("grimmlink-hash");
        request.setDevice("android");
        request.setDeviceId("device-42");
        request.setStartTime(Instant.parse("2026-06-06T10:00:00Z"));
        request.setEndTime(Instant.parse("2026-06-06T10:05:00Z"));
        request.setDurationSeconds(300);
        request.setDurationFormatted("5m");
        request.setStartProgress(1.5f);
        request.setEndProgress(5.7f);
        request.setProgressDelta(4.2f);
        request.setStartLocation("/body/DocFragment[3]");
        request.setEndLocation("/body/DocFragment[5]");
        request.setCurrentPage(23);
        request.setTotalPages(4022);

        grimmlinkFacade.recordReadingSession(request);

        ArgumentCaptor<ReadingSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(sessionCaptor.capture());

        ReadingSessionEntity saved = sessionCaptor.getValue();
        assertNotNull(saved);
        assertEquals("grimmlink-hash", saved.getBookHash());
        assertEquals("android", saved.getDevice());
        assertEquals("device-42", saved.getDeviceId());
        assertEquals(23, saved.getCurrentPage());
        assertEquals(4022, saved.getTotalPages());
        assertEquals(BookFileType.EPUB, saved.getBookType());
    }
}
