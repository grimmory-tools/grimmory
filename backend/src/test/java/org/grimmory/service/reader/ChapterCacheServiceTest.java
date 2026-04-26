package org.grimmory.service.reader;

import org.grimmory.config.AppProperties;
import org.grimmory.service.ArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.grimmory.exception.APIException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChapterCacheServiceTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private ArchiveService archiveService;

    private ChapterCacheService chapterCacheService;

    @BeforeEach
    void setUp() {
        chapterCacheService = new ChapterCacheService(appProperties, archiveService);
    }

    @Test
    void getCachedPage_withTraversal_throwsException() {
        assertThrows(APIException.class, () ->
            chapterCacheService.getCachedPage("../outside", 1)
        );
    }

    @Test
    void getCachedPage_withPathSeparator_throwsException() {
        assertThrows(APIException.class, () ->
            chapterCacheService.getCachedPage("sub/folder", 1)
        );
    }

    @Test
    void hasPage_withTraversal_throwsException() {
        assertThrows(APIException.class, () ->
            chapterCacheService.hasPage("../outside", 1)
        );
    }
}
