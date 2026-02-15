package org.booklore.controller;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookMetadataMapper;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataControllerTest {

    @Mock
    private BookMetadataService bookMetadataService;
    @Mock
    private BookMetadataUpdater bookMetadataUpdater;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private BookMetadataMapper bookMetadataMapper;
    @Mock
    private MetadataMatchService metadataMatchService;
    @Mock
    private DuckDuckGoCoverService duckDuckGoCoverService;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private MetadataManagementService metadataManagementService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private MetadataController metadataController;

    private MetadataUpdateContext captureContextFromUpdate() {
        long bookId = 1L;
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder().build();
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        bookEntity.setMetadata(new BookMetadataEntity());

        when(bookRepository.findAllWithMetadataByIds(java.util.Collections.singleton(bookId))).thenReturn(java.util.List.of(bookEntity));
        when(bookMetadataMapper.toBookMetadata(any(), anyBoolean())).thenReturn(new BookMetadata());

        metadataController.updateMetadata(wrapper, bookId, true);

        ArgumentCaptor<MetadataUpdateContext> captor = ArgumentCaptor.forClass(MetadataUpdateContext.class);
        verify(bookMetadataUpdater).setBookMetadata(captor.capture());
        return captor.getValue();
    }

    @Test
    void updateMetadata_shouldDisableMergingForTagsAndMoods() {
        MetadataUpdateContext context = captureContextFromUpdate();

        assertFalse(context.isMergeTags(), "mergeTags should be false to allow deletion of tags");
        assertFalse(context.isMergeMoods(), "mergeMoods should be false to allow deletion of moods");
    }
}
