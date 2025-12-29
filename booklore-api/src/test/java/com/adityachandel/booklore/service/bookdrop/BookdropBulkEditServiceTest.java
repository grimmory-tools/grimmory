package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BookdropBulkEditRequest;
import com.adityachandel.booklore.model.dto.response.BookdropBulkEditResult;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookdropBulkEditServiceTest {

    @Mock
    private BookdropFileRepository bookdropFileRepository;

    @Mock
    private BookdropMetadataHelper metadataHelper;

    @InjectMocks
    private BookdropBulkEditService bulkEditService;

    @Captor
    private ArgumentCaptor<List<BookdropFileEntity>> filesCaptor;

    private BookdropFileEntity createFileEntity(Long id, String fileName, BookMetadata metadata) {
        BookdropFileEntity entity = new BookdropFileEntity();
        entity.setId(id);
        entity.setFileName(fileName);
        entity.setFilePath("/bookdrop/" + fileName);
        return entity;
    }

    @BeforeEach
    void setUp() {
        when(metadataHelper.getCurrentMetadata(any())).thenReturn(new BookMetadata());
        doNothing().when(metadataHelper).updateFetchedMetadata(any(), any());
    }

    @Test
    void bulkEdit_WithSingleValueFields_ShouldUpdateTextAndNumericFields() {
        BookMetadata existingMetadata = new BookMetadata();
        existingMetadata.setSeriesName("Old Series");
        
        BookdropFileEntity file1 = createFileEntity(1L, "file1.cbz", existingMetadata);
        BookdropFileEntity file2 = createFileEntity(2L, "file2.cbz", existingMetadata);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L, 2L)))
                .thenReturn(List.of(1L, 2L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file1, file2));

        BookMetadata updates = new BookMetadata();
        updates.setSeriesName("New Series");
        updates.setPublisher("Test Publisher");
        updates.setLanguage("en");
        updates.setSeriesTotal(100);

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("seriesName", "publisher", "language", "seriesTotal"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L, 2L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(2, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyUpdated());
        assertEquals(0, result.getFailed());

        verify(metadataHelper, times(2)).updateFetchedMetadata(any(), any());
        verify(bookdropFileRepository, times(1)).saveAll(anyList());
    }

    @Test
    void bulkEdit_WithArrayFieldsMergeMode_ShouldMergeArrays() {
        BookMetadata existingMetadata = new BookMetadata();
        existingMetadata.setAuthors(new LinkedHashSet<>(List.of("Author 1")));
        existingMetadata.setCategories(new LinkedHashSet<>(List.of("Category 1")));

        when(metadataHelper.getCurrentMetadata(any())).thenReturn(existingMetadata);
        
        BookdropFileEntity file = createFileEntity(1L, "file.cbz", existingMetadata);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L)))
                .thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file));

        BookMetadata updates = new BookMetadata();
        updates.setAuthors(new LinkedHashSet<>(List.of("Author 2")));
        updates.setCategories(new LinkedHashSet<>(List.of("Category 2")));

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("authors", "categories"));
        request.setMergeArrays(true);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getSuccessfullyUpdated());
        assertEquals(0, result.getFailed());

        ArgumentCaptor<BookMetadata> metadataCaptor = ArgumentCaptor.forClass(BookMetadata.class);
        verify(metadataHelper).updateFetchedMetadata(any(), metadataCaptor.capture());

        BookMetadata captured = metadataCaptor.getValue();
        assertTrue(captured.getAuthors().contains("Author 1"));
        assertTrue(captured.getAuthors().contains("Author 2"));
        assertTrue(captured.getCategories().contains("Category 1"));
        assertTrue(captured.getCategories().contains("Category 2"));
    }

    @Test
    void bulkEdit_WithArrayFieldsReplaceMode_ShouldReplaceArrays() {
        BookMetadata existingMetadata = new BookMetadata();
        existingMetadata.setAuthors(new LinkedHashSet<>(List.of("Author 1")));

        when(metadataHelper.getCurrentMetadata(any())).thenReturn(existingMetadata);
        
        BookdropFileEntity file = createFileEntity(1L, "file.cbz", existingMetadata);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L)))
                .thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file));

        BookMetadata updates = new BookMetadata();
        updates.setAuthors(new LinkedHashSet<>(List.of("Author 2")));

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("authors"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L));

        bulkEditService.bulkEdit(request);

        ArgumentCaptor<BookMetadata> metadataCaptor = ArgumentCaptor.forClass(BookMetadata.class);
        verify(metadataHelper).updateFetchedMetadata(any(), metadataCaptor.capture());

        BookMetadata captured = metadataCaptor.getValue();
        assertFalse(captured.getAuthors().contains("Author 1"));
        assertTrue(captured.getAuthors().contains("Author 2"));
        assertEquals(1, captured.getAuthors().size());
    }

    @Test
    void bulkEdit_WithDisabledFields_ShouldNotUpdateThoseFields() {
        BookMetadata existingMetadata = new BookMetadata();
        existingMetadata.setSeriesName("Original Series");
        existingMetadata.setPublisher("Original Publisher");

        when(metadataHelper.getCurrentMetadata(any())).thenReturn(existingMetadata);
        
        BookdropFileEntity file = createFileEntity(1L, "file.cbz", existingMetadata);

        when(metadataHelper.resolveFileIds(false, null, List.of(1L)))
                .thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file));

        BookMetadata updates = new BookMetadata();
        updates.setSeriesName("New Series");
        updates.setPublisher("New Publisher");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("seriesName"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L));

        bulkEditService.bulkEdit(request);

        ArgumentCaptor<BookMetadata> metadataCaptor = ArgumentCaptor.forClass(BookMetadata.class);
        verify(metadataHelper).updateFetchedMetadata(any(), metadataCaptor.capture());

        BookMetadata captured = metadataCaptor.getValue();
        assertEquals("New Series", captured.getSeriesName());
        assertEquals("Original Publisher", captured.getPublisher());
    }

    @Test
    void bulkEdit_WithSelectAll_ShouldProcessAllFiles() {
        BookdropFileEntity file1 = createFileEntity(1L, "file1.cbz", new BookMetadata());
        BookdropFileEntity file2 = createFileEntity(2L, "file2.cbz", new BookMetadata());
        BookdropFileEntity file3 = createFileEntity(3L, "file3.cbz", new BookMetadata());

        when(metadataHelper.resolveFileIds(true, List.of(2L), null))
                .thenReturn(List.of(1L, 3L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file1, file3));

        BookMetadata updates = new BookMetadata();
        updates.setLanguage("en");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("language"));
        request.setMergeArrays(false);
        request.setSelectAll(true);
        request.setExcludedIds(List.of(2L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(2, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyUpdated());
        verify(metadataHelper, times(2)).updateFetchedMetadata(any(), any());
    }

    @Test
    void bulkEdit_WithOneFileError_ShouldContinueWithOthers() {
        BookdropFileEntity file1 = createFileEntity(1L, "file1.cbz", new BookMetadata());
        BookdropFileEntity file2 = createFileEntity(2L, "file2.cbz", new BookMetadata());
        BookdropFileEntity file3 = createFileEntity(3L, "file3.cbz", new BookMetadata());

        when(metadataHelper.resolveFileIds(false, null, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file1, file2, file3));

        doThrow(new RuntimeException("JSON serialization error"))
                .when(metadataHelper).updateFetchedMetadata(eq(file2), any());

        BookMetadata updates = new BookMetadata();
        updates.setLanguage("en");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("language"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L, 2L, 3L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(3, result.getTotalFiles());
        assertEquals(2, result.getSuccessfullyUpdated());
        assertEquals(1, result.getFailed());

        verify(bookdropFileRepository).saveAll(filesCaptor.capture());
        List<BookdropFileEntity> savedFiles = filesCaptor.getValue();
        assertEquals(2, savedFiles.size());
        assertTrue(savedFiles.stream().anyMatch(f -> f.getId().equals(1L)));
        assertTrue(savedFiles.stream().anyMatch(f -> f.getId().equals(3L)));
        assertFalse(savedFiles.stream().anyMatch(f -> f.getId().equals(2L)));
    }

    @Test
    void bulkEdit_WithEmptyEnabledFields_ShouldNotUpdateAnything() {
        BookdropFileEntity file = createFileEntity(1L, "file.cbz", new BookMetadata());

        when(metadataHelper.resolveFileIds(false, null, List.of(1L)))
                .thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(List.of(file));

        BookMetadata updates = new BookMetadata();
        updates.setSeriesName("New Series");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Collections.emptySet());
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(List.of(1L));

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(1, result.getSuccessfullyUpdated());
        
        ArgumentCaptor<BookMetadata> metadataCaptor = ArgumentCaptor.forClass(BookMetadata.class);
        verify(metadataHelper).updateFetchedMetadata(any(), metadataCaptor.capture());

        assertNull(metadataCaptor.getValue().getSeriesName());
    }

    @Test
    void bulkEdit_WithLargeSelection_ShouldProcessInBatches() {
        List<BookdropFileEntity> batch1 = new ArrayList<>();
        List<BookdropFileEntity> batch2 = new ArrayList<>();
        List<BookdropFileEntity> batch3 = new ArrayList<>();
        List<Long> manyIds = new ArrayList<>();
        
        for (long i = 1; i <= 1500; i++) {
            manyIds.add(i);
            BookdropFileEntity file = createFileEntity(i, "file" + i + ".cbz", new BookMetadata());
            if (i <= 500) {
                batch1.add(file);
            } else if (i <= 1000) {
                batch2.add(file);
            } else {
                batch3.add(file);
            }
        }

        when(metadataHelper.resolveFileIds(false, null, manyIds))
                .thenReturn(manyIds);
        
        when(bookdropFileRepository.findAllById(anyList()))
                .thenReturn(batch1, batch2, batch3);

        BookMetadata updates = new BookMetadata();
        updates.setLanguage("en");

        BookdropBulkEditRequest request = new BookdropBulkEditRequest();
        request.setFields(updates);
        request.setEnabledFields(Set.of("language"));
        request.setMergeArrays(false);
        request.setSelectAll(false);
        request.setSelectedIds(manyIds);

        BookdropBulkEditResult result = bulkEditService.bulkEdit(request);

        assertEquals(1500, result.getTotalFiles());
        assertEquals(1500, result.getSuccessfullyUpdated());
        assertEquals(0, result.getFailed());
        
        verify(bookdropFileRepository, times(3)).findAllById(anyList());
        verify(bookdropFileRepository, times(3)).saveAll(anyList());
    }
}
