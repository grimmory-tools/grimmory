package org.booklore.controller;

import org.booklore.model.enums.MetadataLockPolicy;
import org.booklore.service.metadata.BookCoverService;
import org.booklore.service.metadata.DuckDuckGoCoverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookCoverControllerTest {

    @Mock
    private BookCoverService bookCoverService;

    @Mock
    private DuckDuckGoCoverService duckDuckGoCoverService;

    @InjectMocks
    private BookCoverController controller;

    @Test
    void manualCoverActionsForwardTheRequestedLockPolicy() {
        MultipartFile file = mock(MultipartFile.class);
        MetadataLockPolicy policy = MetadataLockPolicy.ALLOW_LOCKED_FIELD_EDITS;

        controller.uploadCoverFromFile(1L, file, policy);
        controller.regenerateCovers(1L, policy);
        controller.generateCustomCover(1L, policy);
        controller.uploadAudiobookCoverFromFile(1L, file, policy);
        controller.regenerateAudiobookCover(1L, policy);
        controller.generateCustomAudiobookCover(1L, policy);

        verify(bookCoverService).updateCoverFromFile(1L, file, policy);
        verify(bookCoverService).regenerateCover(1L, policy);
        verify(bookCoverService).generateCustomCover(1L, policy);
        verify(bookCoverService).updateAudiobookCoverFromFile(1L, file, policy);
        verify(bookCoverService).regenerateAudiobookCover(1L, policy);
        verify(bookCoverService).generateCustomAudiobookCover(1L, policy);
    }

    @Test
    void manualCoverEndpointsDefaultToRespectLocks() throws NoSuchMethodException {
        assertLockBehaviorDefault("uploadCoverFromFile", Long.class, MultipartFile.class, MetadataLockPolicy.class);
        assertLockBehaviorDefault("regenerateCovers", Long.class, MetadataLockPolicy.class);
        assertLockBehaviorDefault("generateCustomCover", Long.class, MetadataLockPolicy.class);
        assertLockBehaviorDefault("uploadAudiobookCoverFromFile", Long.class, MultipartFile.class, MetadataLockPolicy.class);
        assertLockBehaviorDefault("regenerateAudiobookCover", Long.class, MetadataLockPolicy.class);
        assertLockBehaviorDefault("generateCustomAudiobookCover", Long.class, MetadataLockPolicy.class);
    }

    private void assertLockBehaviorDefault(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = BookCoverController.class.getMethod(methodName, parameterTypes);
        RequestParam annotation = method.getParameters()[parameterTypes.length - 1].getAnnotation(RequestParam.class);
        assertThat(annotation.name()).isEqualTo("lockBehavior");
        assertThat(annotation.defaultValue()).isEqualTo("RESPECT_LOCKS");
    }
}
