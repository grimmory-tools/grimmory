package org.booklore.service.metadata.sidecar;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.sidecar.SidecarBookMetadata;
import org.booklore.model.dto.sidecar.SidecarCoverInfo;
import org.booklore.model.dto.sidecar.SidecarMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.metadata.BookCoverService;
import org.booklore.service.metadata.BookMetadataUpdater;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SidecarServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private SidecarMetadataReader sidecarReader;
    @Mock
    private SidecarMetadataWriter sidecarWriter;
    @Mock
    private SidecarMetadataMapper sidecarMapper;
    @Mock
    private BookMetadataUpdater bookMetadataUpdater;
    @Mock
    private BookCoverService bookCoverService;

    @InjectMocks
    private SidecarService sidecarService;

    private BookEntity mockBook(long id) {
        BookEntity book = mock(BookEntity.class);
        lenient().when(book.getId()).thenReturn(id);
        when(book.getFullFilePath()).thenReturn(Path.of("/books/repro/libro.pdf"));
        return book;
    }

    private SidecarMetadata sidecarMetadata() {
        return SidecarMetadata.builder()
                .metadata(SidecarBookMetadata.builder().title("Libro de Prueba").build())
                .cover(SidecarCoverInfo.builder().source("external").path("libro.cover.jpg").build())
                .build();
    }

    @Nested
    class ImportFromSidecar {

        @Test
        void importsMetadataAndAppliesCoverWhenCoverFileExists() {
            BookEntity book = mockBook(1L);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(sidecarReader.readSidecarMetadata(any())).thenReturn(Optional.of(sidecarMetadata()));
            when(sidecarMapper.toBookMetadata(any())).thenReturn(mock(BookMetadata.class));
            byte[] coverBytes = {1, 2, 3};
            when(sidecarReader.readSidecarCover(any(), any())).thenReturn(coverBytes);

            sidecarService.importFromSidecar(1L);

            verify(bookMetadataUpdater).setBookMetadata(any());
            verify(bookCoverService).updateCoverFromBytes(1L, coverBytes);
        }

        @Test
        void passesSidecarMetadataAndBookPathToCoverReader() {
            BookEntity book = mockBook(1L);
            SidecarMetadata sidecar = sidecarMetadata();
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(sidecarReader.readSidecarMetadata(any())).thenReturn(Optional.of(sidecar));
            when(sidecarMapper.toBookMetadata(any())).thenReturn(mock(BookMetadata.class));
            when(sidecarReader.readSidecarCover(any(), any())).thenReturn(new byte[]{1});

            sidecarService.importFromSidecar(1L);

            ArgumentCaptor<SidecarMetadata> captor = ArgumentCaptor.forClass(SidecarMetadata.class);
            verify(sidecarReader).readSidecarCover(eq(Path.of("/books/repro/libro.pdf")), captor.capture());
            assertThat(captor.getValue()).isSameAs(sidecar);
        }

        @Test
        void importsMetadataButSkipsCoverWhenNoCoverFile() {
            BookEntity book = mockBook(1L);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(sidecarReader.readSidecarMetadata(any())).thenReturn(Optional.of(sidecarMetadata()));
            when(sidecarMapper.toBookMetadata(any())).thenReturn(mock(BookMetadata.class));
            when(sidecarReader.readSidecarCover(any(), any())).thenReturn(null);

            sidecarService.importFromSidecar(1L);

            verify(bookMetadataUpdater).setBookMetadata(any());
            verify(bookCoverService, never()).updateCoverFromBytes(any(), any());
        }

        @Test
        void coverImportFailureDoesNotBreakMetadataImport() {
            BookEntity book = mockBook(1L);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
            when(sidecarReader.readSidecarMetadata(any())).thenReturn(Optional.of(sidecarMetadata()));
            when(sidecarMapper.toBookMetadata(any())).thenReturn(mock(BookMetadata.class));
            when(sidecarReader.readSidecarCover(any(), any())).thenReturn(new byte[]{1});
            doThrow(new RuntimeException("cover locked"))
                    .when(bookCoverService).updateCoverFromBytes(any(), any());

            sidecarService.importFromSidecar(1L);

            verify(bookMetadataUpdater).setBookMetadata(any());
        }
    }

    @Nested
    class BulkImport {

        @Test
        void importsCoverForEachBookWithSidecarCover() {
            BookEntity book1 = mockBook(1L);
            BookEntity book2 = mockBook(2L);
            when(libraryRepository.findById(10L)).thenReturn(Optional.of(new LibraryEntity()));
            when(bookRepository.findAllByLibraryIdWithFiles(10L)).thenReturn(List.of(book1, book2));
            when(sidecarReader.readSidecarMetadata(any())).thenReturn(Optional.of(sidecarMetadata()));
            when(sidecarMapper.toBookMetadata(any())).thenReturn(mock(BookMetadata.class));
            when(sidecarReader.readSidecarCover(any(), any())).thenReturn(new byte[]{9, 9, 9});

            int imported = sidecarService.bulkImport(10L);

            assertThat(imported).isEqualTo(2);
            verify(bookCoverService).updateCoverFromBytes(1L, new byte[]{9, 9, 9});
            verify(bookCoverService).updateCoverFromBytes(2L, new byte[]{9, 9, 9});
        }

        @Test
        void doesNotFailWholeBatchWhenCoverImportFails() {
            BookEntity book = mockBook(1L);
            when(libraryRepository.findById(10L)).thenReturn(Optional.of(new LibraryEntity()));
            when(bookRepository.findAllByLibraryIdWithFiles(10L)).thenReturn(List.of(book));
            when(sidecarReader.readSidecarMetadata(any())).thenReturn(Optional.of(sidecarMetadata()));
            when(sidecarMapper.toBookMetadata(any())).thenReturn(mock(BookMetadata.class));
            when(sidecarReader.readSidecarCover(any(), any())).thenReturn(new byte[]{1});
            doThrow(new RuntimeException("boom"))
                    .when(bookCoverService).updateCoverFromBytes(any(), any());

            int imported = sidecarService.bulkImport(10L);

            assertThat(imported).isEqualTo(1);
            verify(bookCoverService).updateCoverFromBytes(1L, new byte[]{1});
        }
    }
}
