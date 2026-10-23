package org.booklore.service.metadata.sidecar;

import org.booklore.model.dto.sidecar.SidecarCoverInfo;
import org.booklore.model.dto.sidecar.SidecarMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.metadata.BookCoverService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class SidecarCoverImportTest {

    @Test
    void importingSidecarAppliesAndSavesItsCover() {
        Path bookPath = Path.of("/books/test.pdf");
        BookEntity book = mock(BookEntity.class);
        when(book.getFullFilePath()).thenReturn(bookPath);
        BookRepository books = mock(BookRepository.class);
        when(books.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        SidecarMetadata sidecar = SidecarMetadata.builder()
                .cover(SidecarCoverInfo.builder().path("test.cover.jpg").build()).build();
        byte[] coverBytes = {1, 2, 3};
        SidecarMetadataReader reader = mock(SidecarMetadataReader.class);
        when(reader.readSidecarMetadata(bookPath)).thenReturn(Optional.of(sidecar));
        when(reader.readSidecarCover(bookPath, sidecar)).thenReturn(coverBytes);
        BookCoverService covers = mock(BookCoverService.class);
        when(covers.applyUploadedCoverSlots(book, coverBytes, true)).thenReturn(true);
        SidecarService importer = new SidecarService(books, null, reader,
                mock(SidecarMetadataWriter.class), new SidecarMetadataMapper(), null, covers);

        importer.importFromSidecar(1L);

        verify(covers).applyUploadedCoverSlots(book, coverBytes, true);
        verify(covers).saveUpdatedCovers(List.of(book));
    }
}
