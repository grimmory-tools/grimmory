package org.booklore.service.kobo;

import org.booklore.model.dto.kobo.KoboSpanPositionMap;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.KoboSpanMapEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.KoboSpanMapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoboSpanMapServiceTest {

    @Mock
    private KoboSpanMapRepository koboSpanMapRepository;

    @Mock
    private KoboSpanMapExtractionService koboSpanMapExtractionService;

    private KoboSpanMapService service;

    @BeforeEach
    void setUp() {
        service = new KoboSpanMapService(koboSpanMapRepository, koboSpanMapExtractionService);
    }

    @Test
    void computeAndStoreIfNeeded_SkipsWhenExistingHashMatches() throws Exception {
        BookFileEntity bookFile = createBookFile("hash-1");
        KoboSpanMapEntity existing = KoboSpanMapEntity.builder()
                .bookFile(bookFile)
                .fileHash("hash-1")
                .spanMap(spanMap())
                .createdAt(Instant.now())
                .build();
        when(koboSpanMapRepository.findByBookFileId(bookFile.getId())).thenReturn(Optional.of(existing));

        service.computeAndStoreIfNeeded(bookFile, new File("ignored.kepub.epub"));

        verify(koboSpanMapExtractionService, never()).extractFromKepub(any());
        verify(koboSpanMapRepository, never()).save(any());
    }

    @Test
    void computeAndStoreIfNeeded_ExtractsAndSavesWhenHashChanges() throws Exception {
        BookFileEntity bookFile = createBookFile("hash-2");
        KoboSpanMapEntity existing = KoboSpanMapEntity.builder()
                .id(99L)
                .bookFile(bookFile)
                .fileHash("old-hash")
                .spanMap(spanMap())
                .createdAt(Instant.parse("2024-01-01T00:00:00Z"))
                .build();
        KoboSpanPositionMap newMap = new KoboSpanPositionMap(List.of(
                new KoboSpanPositionMap.Chapter("OPS/chapter2.xhtml", "OPS/chapter2.xhtml", 0, 0f, 1f,
                        List.of(new KoboSpanPositionMap.Span("kobo.2.1", 0.5f)))
        ));

        when(koboSpanMapRepository.findByBookFileId(bookFile.getId())).thenReturn(Optional.of(existing));
        when(koboSpanMapExtractionService.extractFromKepub(any())).thenReturn(newMap);

        service.computeAndStoreIfNeeded(bookFile, tempKepubFile());

        ArgumentCaptor<KoboSpanMapEntity> captor = ArgumentCaptor.forClass(KoboSpanMapEntity.class);
        verify(koboSpanMapRepository).save(captor.capture());
        assertEquals(99L, captor.getValue().getId());
        assertEquals("hash-2", captor.getValue().getFileHash());
        assertEquals(newMap, captor.getValue().getSpanMap());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), captor.getValue().getCreatedAt());
    }

    @Test
    void getValidMap_ReturnsOnlyWhenHashMatches() {
        BookFileEntity bookFile = createBookFile("hash-3");
        KoboSpanMapEntity stored = KoboSpanMapEntity.builder()
                .bookFile(bookFile)
                .fileHash("hash-3")
                .spanMap(spanMap())
                .createdAt(Instant.now())
                .build();
        when(koboSpanMapRepository.findByBookFileId(bookFile.getId())).thenReturn(Optional.of(stored));

        Optional<KoboSpanPositionMap> validMap = service.getValidMap(bookFile);

        assertTrue(validMap.isPresent());
        assertEquals(spanMap(), validMap.get());

        stored.setFileHash("stale-hash");
        Optional<KoboSpanPositionMap> staleMap = service.getValidMap(bookFile);

        assertFalse(staleMap.isPresent());
    }

    @Test
    void getValidMaps_SkipsEntriesWithNullSpanMap() {
        BookFileEntity bookFile = createBookFile("hash-4");
        KoboSpanMapEntity stored = KoboSpanMapEntity.builder()
                .bookFile(bookFile)
                .fileHash("hash-4")
                .spanMap(null)
                .createdAt(Instant.now())
                .build();
        when(koboSpanMapRepository.findByBookFileIdIn(bookFilesById(bookFile).keySet())).thenReturn(List.of(stored));

        Map<Long, KoboSpanPositionMap> validMaps = service.getValidMaps(bookFilesById(bookFile));

        assertTrue(validMaps.isEmpty());
    }

    private BookFileEntity createBookFile(String hash) {
        BookFileEntity bookFile = new BookFileEntity();
        bookFile.setId(42L);
        bookFile.setBookType(BookFileType.EPUB);
        bookFile.setCurrentHash(hash);
        return bookFile;
    }

    private KoboSpanPositionMap spanMap() {
        return new KoboSpanPositionMap(List.of(
                new KoboSpanPositionMap.Chapter("OPS/chapter1.xhtml", "OPS/chapter1.xhtml", 0, 0f, 1f,
                        List.of(new KoboSpanPositionMap.Span("kobo.1.1", 0.2f)))
        ));
    }

    private Map<Long, BookFileEntity> bookFilesById(BookFileEntity bookFile) {
        return Map.of(bookFile.getId(), bookFile);
    }

    private File tempKepubFile() throws Exception {
        File file = File.createTempFile("kobo-span-map", ".kepub.epub");
        file.deleteOnExit();
        return file;
    }
}
