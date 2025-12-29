package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.metadata.*;
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
