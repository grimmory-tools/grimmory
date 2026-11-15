package org.booklore.controller;

import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.model.enums.MetadataLockPolicy;
import org.booklore.service.metadata.BookMetadataService;
import org.booklore.service.metadata.MetadataManagementService;
import org.booklore.service.metadata.MetadataMatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataControllerTest {

    @Mock
    private BookMetadataService bookMetadataService;
    @Mock
    private MetadataMatchService metadataMatchService;
    @Mock
    private MetadataManagementService metadataManagementService;

    @InjectMocks
    private MetadataController metadataController;

    @Test
    void updateMetadata_defaultsOmittedLockPolicyToRespectLocks() throws NoSuchMethodException {
        var method = MetadataController.class.getMethod("updateMetadata", MetadataUpdateWrapper.class, long.class, boolean.class, MetadataReplaceMode.class, MetadataLockPolicy.class);
        RequestParam lockBehavior = method.getParameters()[4].getAnnotation(RequestParam.class);

        assertThat(lockBehavior.name()).isEqualTo("lockBehavior");
        assertThat(lockBehavior.defaultValue()).isEqualTo("RESPECT_LOCKS");
    }

    @Test
    void updateMetadata_delegatesToService() {
        long bookId = 1L;
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder().build();
        MetadataReplaceMode replaceMode = MetadataReplaceMode.REPLACE_ALL;
        BookMetadata expected = new BookMetadata();

        when(bookMetadataService.updateBookMetadata(bookId, wrapper, true, replaceMode, MetadataLockPolicy.RESPECT_LOCKS))
                .thenReturn(expected);

        ResponseEntity<BookMetadata> response = metadataController.updateMetadata(wrapper, bookId, true, replaceMode, MetadataLockPolicy.RESPECT_LOCKS);

        assertThat(response.getBody()).isSameAs(expected);
        verify(bookMetadataService).updateBookMetadata(bookId, wrapper, true, replaceMode, MetadataLockPolicy.RESPECT_LOCKS);
    }

    @Test
    void updateMetadata_passesAllowLockedFieldEditsToService() {
        long bookId = 1L;
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder().build();
        MetadataReplaceMode replaceMode = MetadataReplaceMode.REPLACE_ALL;
        BookMetadata expected = new BookMetadata();
        when(bookMetadataService.updateBookMetadata(bookId, wrapper, false, replaceMode, MetadataLockPolicy.ALLOW_LOCKED_FIELD_EDITS))
                .thenReturn(expected);

        ResponseEntity<BookMetadata> response = metadataController.updateMetadata(
                wrapper, bookId, false, replaceMode, MetadataLockPolicy.ALLOW_LOCKED_FIELD_EDITS);

        assertThat(response.getBody()).isSameAs(expected);
        verify(bookMetadataService).updateBookMetadata(bookId, wrapper, false, replaceMode, MetadataLockPolicy.ALLOW_LOCKED_FIELD_EDITS);
    }

    @Test
    void updateMetadata_passesReplaceModeToService() {
        long bookId = 1L;
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder().build();
        MetadataReplaceMode replaceMode = MetadataReplaceMode.REPLACE_WHEN_PROVIDED;
        BookMetadata expected = new BookMetadata();

        when(bookMetadataService.updateBookMetadata(bookId, wrapper, false, replaceMode, MetadataLockPolicy.RESPECT_LOCKS))
                .thenReturn(expected);

        metadataController.updateMetadata(wrapper, bookId, false, replaceMode, MetadataLockPolicy.RESPECT_LOCKS);

        verify(bookMetadataService).updateBookMetadata(bookId, wrapper, false, replaceMode, MetadataLockPolicy.RESPECT_LOCKS);
    }
}
