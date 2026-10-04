package org.booklore.service.icon;

import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundledIconServiceTest {

    @TempDir Path dataDir;
    @Mock private FileService fileService;

    private BundledIconService service;
    private Path iconDir;

    @BeforeEach
    void setUp() {
        iconDir = dataDir.resolve("icons").resolve("svg");
        lenient().when(fileService.getIconsSvgFolder()).thenReturn(iconDir.toString());
        service = new BundledIconService(fileService);
    }

    @Test
    void everyBundledIconIsInstalledOnAnEmptyDataDirectory() throws Exception {
        service.seedBundledIcons();

        for (String icon : BundledIconService.BUNDLED_ICONS) {
            Path file = iconDir.resolve(icon + ".svg");
            assertTrue(Files.exists(file), icon);
            String svg = Files.readString(file).trim();
            assertTrue(svg.startsWith("<svg") || svg.startsWith("<?xml"), icon);
            assertTrue(svg.contains("</svg>"), icon);
        }
    }

    @Test
    void theShelfIconsAreAmongTheBundledOnes() {
        assertTrue(BundledIconService.BUNDLED_ICONS.contains(BundledIconService.KOBO_ICON));
        assertTrue(BundledIconService.BUNDLED_ICONS.contains(BundledIconService.KOBO_ICON_ALTERNATE));
        assertTrue(BundledIconService.BUNDLED_ICONS.contains(BundledIconService.KOREADER_ICON));
        assertTrue(BundledIconService.BUNDLED_ICONS.contains(BundledIconService.AUDIOBOOKSHELF_ICON));
    }

    @Test
    void anIconTheUserHasReplacedIsLeftAlone() throws Exception {
        Files.createDirectories(iconDir);
        Path mine = iconDir.resolve(BundledIconService.KOBO_ICON + ".svg");
        Files.writeString(mine, "<svg>mine</svg>");

        service.seedBundledIcons();

        assertEquals("<svg>mine</svg>", Files.readString(mine));
        assertTrue(Files.exists(iconDir.resolve(BundledIconService.KOREADER_ICON + ".svg")));
    }

    @Test
    void aLeftoverPartialCopyDoesNotBlockTheInstall() throws Exception {
        Files.createDirectories(iconDir);
        Path partial = iconDir.resolve(BundledIconService.KOREADER_ICON + ".svg.part");
        Files.writeString(partial, "<svg");

        service.seedBundledIcons();

        assertTrue(Files.readString(iconDir.resolve(BundledIconService.KOREADER_ICON + ".svg")).contains("</svg>"));
        assertFalse(Files.exists(partial));
    }

    @Test
    void seedingTwiceChangesNothingTheSecondTime() throws Exception {
        service.seedBundledIcons();
        Path file = iconDir.resolve(BundledIconService.KOREADER_ICON + ".svg");
        var firstWrite = Files.getLastModifiedTime(file);

        service.seedBundledIcons();

        assertEquals(firstWrite, Files.getLastModifiedTime(file));
    }
}
