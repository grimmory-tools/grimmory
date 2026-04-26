package org.grimmory.service;

import org.booklore.config.AppProperties;
import org.booklore.exception.APIException;
import org.booklore.model.dto.request.SvgIconCreateRequest;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IconServiceTest {

    private static final String TEST_DIR = "test-icons";
    private static final String SVG_DIR = "svg";
    private static final String SVG_NAME = "testicon";
    private static final String SVG_DATA = "<svg><rect width=\"100\" height=\"100\"/></svg>";
    private static final String SVG_DATA_XML = "<?xml version=\"1.0\"?><svg><rect width=\"100\" height=\"100\"/></svg>";
    private static final String INVALID_SVG_DATA = "<rect></rect>";
    private static final String INVALID_SVG_DATA_NO_END = "<svg><rect></rect>";

    private IconService iconService;
    private Path iconsSvgPath;

    @BeforeAll
    void setup() throws IOException {
        AppProperties appProperties = new AppProperties() {
            @Override
            public String getPathConfig() {
                return TEST_DIR;
            }
        };
        iconService = new IconService(appProperties);
        iconsSvgPath = Paths.get(TEST_DIR, "icons", SVG_DIR);
        Files.createDirectories(iconsSvgPath);
    }

    @AfterEach
    void cleanup() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(iconsSvgPath)) {
            for (Path entry : stream) {
                Files.deleteIfExists(entry);
            }
        }
        iconService.getSvgCache().invalidateAll();
    }

    @AfterAll
    void teardown() throws IOException {
        if (Files.exists(iconsSvgPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(iconsSvgPath)) {
                for (Path entry : stream) {
                    Files.deleteIfExists(entry);
                }
            }
            Files.deleteIfExists(iconsSvgPath);
            Files.deleteIfExists(iconsSvgPath.getParent());
            Files.deleteIfExists(Paths.get(TEST_DIR));
        }
    }

    @Test
    void saveSvgIcon_validSvg_savesFileAndCaches() {
        SvgIconCreateRequest req = new SvgIconCreateRequest();
        req.setSvgName(SVG_NAME);
        req.setSvgData(SVG_DATA);
        iconService.saveSvgIcon(req);
        Path filePath = iconsSvgPath.resolve(SVG_NAME + ".svg");
        assertTrue(Files.exists(filePath));
        assertEquals(SVG_DATA, iconService.getSvgIcon(SVG_NAME));
    }

    @Test
    void saveSvgIcon_validXmlSvg_savesFileAndCaches() {
        SvgIconCreateRequest req = new SvgIconCreateRequest();
        req.setSvgName(SVG_NAME);
        req.setSvgData(SVG_DATA_XML);
        iconService.saveSvgIcon(req);
        Path filePath = iconsSvgPath.resolve(SVG_NAME + ".svg");
        assertTrue(Files.exists(filePath));
        assertEquals(SVG_DATA_XML, iconService.getSvgIcon(SVG_NAME));
    }

    @Test
    void saveSvgIcon_invalidSvg_throwsException() {
        SvgIconCreateRequest req = new SvgIconCreateRequest();
        req.setSvgName(SVG_NAME);
        req.setSvgData(INVALID_SVG_DATA);
        assertThrows(APIException.class, () -> iconService.saveSvgIcon(req));
    }

    @Test
    void saveSvgIcon_missingEndTag_throwsException() {
        SvgIconCreateRequest req = new SvgIconCreateRequest();
        req.setSvgName(SVG_NAME);
        req.setSvgData(INVALID_SVG_DATA_NO_END);
        assertThrows(APIException.class, () -> iconService.saveSvgIcon(req));
    }

    @Test
    void getSvgIcon_existing_returnsContent() {
        SvgIconCreateRequest req = new SvgIconCreateRequest();
        req.setSvgName(SVG_NAME);
        req.setSvgData(SVG_DATA);
        iconService.saveSvgIcon(req);
        String svg = iconService.getSvgIcon(SVG_NAME);
        assertEquals(SVG_DATA, svg);
    }

    @Test
    void getSvgIcon_nonExisting_throwsException() {
        assertThrows(APIException.class, () -> iconService.getSvgIcon("nonexistent"));
    }

    @Test
    void deleteSvgIcon_existing_deletesFileAndCache() {
        SvgIconCreateRequest req = new SvgIconCreateRequest();
        req.setSvgName(SVG_NAME);
        req.setSvgData(SVG_DATA);
        iconService.saveSvgIcon(req);
        iconService.deleteSvgIcon(SVG_NAME);
        Path filePath = iconsSvgPath.resolve(SVG_NAME + ".svg");
        assertFalse(Files.exists(filePath));
        assertThrows(APIException.class, () -> iconService.getSvgIcon(SVG_NAME));
    }

    @Test
    void deleteSvgIcon_nonExisting_throwsException() {
        assertThrows(APIException.class, () -> iconService.deleteSvgIcon("nonexistent"));
    }

    @Test
    void getIconNames_pagination_returnsCorrectPage() {
        for (int i = 0; i < 5; i++) {
            SvgIconCreateRequest req = new SvgIconCreateRequest();
            req.setSvgName("icon" + i);
            req.setSvgData(SVG_DATA);
            iconService.saveSvgIcon(req);
        }
        Page<String> page = iconService.getIconNames(0, 2);
        assertEquals(2, page.getContent().size());
        assertEquals(5, page.getTotalElements());
        assertEquals(List.of("icon0", "icon1"), page.getContent());
        Page<String> page2 = iconService.getIconNames(2, 2);
        assertEquals(1, page2.getContent().size());
        assertEquals(List.of("icon4"), page2.getContent());
    }

    @Test
    void getIconNames_invalidPageParams_throwsException() {
        assertThrows(APIException.class, () -> iconService.getIconNames(-1, 2));
        assertThrows(APIException.class, () -> iconService.getIconNames(0, 0));
    }

    @Test
    void normalizeFilename_invalid_throwsException() {
        assertThrows(APIException.class, () -> iconService.getSvgIcon(""));
        assertThrows(APIException.class, () -> iconService.getSvgIcon(null));
    }

    @Test
    void cacheEviction_worksWhenMaxSizeExceeded() {
        iconService.getSvgCache().invalidateAll();

        for (int i = 0; i < 1002; i++) {
            SvgIconCreateRequest req = new SvgIconCreateRequest();
            req.setSvgName("icon" + i);
            req.setSvgData(SVG_DATA);
            iconService.saveSvgIcon(req);
        }
        assertEquals(1002, iconService.getIconNames(0, 2000).getContent().size());
        iconService.getSvgCache().cleanUp();
        assertTrue(iconService.getSvgCache().estimatedSize() <= 200);
    }

    @Test
    void updateExistingIcon_doesNotIncreaseCache() {
        SvgIconCreateRequest req = new SvgIconCreateRequest();
        req.setSvgName(SVG_NAME);
        req.setSvgData(SVG_DATA);
        iconService.saveSvgIcon(req);

        long initialSize = iconService.getSvgCache().estimatedSize();

        req.setSvgData("<svg><circle r=\"50\"/></svg>");
        assertThrows(APIException.class, () -> iconService.saveSvgIcon(req));

        assertEquals(initialSize, iconService.getSvgCache().estimatedSize());
    }
}
