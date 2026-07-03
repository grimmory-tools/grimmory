package org.booklore.service.migration.migrations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.service.migration.Migration;
import org.booklore.util.FileService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveBundledCustomSvgIconsMigration implements Migration {

    private static final Map<String, String> BUNDLED_ICON_CHECKSUMS = Map.ofEntries(
            Map.entry("atom.svg", "843e5064fe13d627ce6308c6b98ddd0504c58a43804331c5d427e7a02af44b97"),
            Map.entry("banana.svg", "e4cb5bb046ff5704b50b2db8c588e0eda085b382a546544f5e8c169cd5e15427"),
            Map.entry("beef.svg", "5fe03174da46d9b690133e14d11b1d844b4f74d7a3777ab4cdfdf33bf83e8071"),
            Map.entry("brain.svg", "68f842059a4c6abc797d8c077d49ad68b3ace3e8a0b8d1582b3370d54a6c7ce9"),
            Map.entry("chef-hat.svg", "83ebc3166d302a08c405bb574fcbdef21eddbed54fa459596eb49eaf6c6118c3"),
            Map.entry("drama.svg", "4b327cd5d1146c3bee2880a6acec7c620c885f4d15e37c31a01cd4592c3f34b5"),
            Map.entry("ferris-wheel.svg", "6eb51f5a34302c29f95c08e5dac26c6585ba9026003bc34424bcf68b0472e3c8"),
            Map.entry("flame-kindling.svg", "91a03c77ddbc57cd098d581dbb88139cf354dcb553e71299cffec53b71678626"),
            Map.entry("ghost.svg", "9c5f52f7447ad08b72554489c1bdc4916e752e46df0ccdc4d91784ff923a02d0"),
            Map.entry("hamburger.svg", "f36ac8ca52aa1b8a550b53625c4f01b669f29ac8d6e2395ffd6858e2c600f3ce"),
            Map.entry("plane.svg", "9073c0b4853d78a3c747b7d093a651351371b5efd1cfeca0412173c394106f92"),
            Map.entry("rocket.svg", "11352fe93a10366bf1abe5be2c005ccfa308c1685f1a8e65a43ed227355d259d"),
            Map.entry("roller-coaster.svg", "0f210f8ca636f44236575ac3ba0ed56fca72abe059d456b6fd1474818e93b6be"),
            Map.entry("rose.svg", "5ea685fed17a1e02ebcb2b620b508a1c632b6eb3358aea34aae867434d98cf09"),
            Map.entry("skull.svg", "b8c24822932c8c0b5a8e8516d654c54c5919de82e7ecdd353ab7dada6c52e540"),
            Map.entry("snail.svg", "72e47239e0f580600afa0dcc559a501065e8dbad5fade0497e47da4ffa4f86a8"),
            Map.entry("swords.svg", "954d6e4fd32f6d527deea3cda331456dd12e9df16c06301c34506e818c588373"),
            Map.entry("tent-tree.svg", "d6b221d86d3002c1dd0a9af8c7649bd21f876a6c6807117b6229444175468b5c"),
            Map.entry("tree-palm.svg", "59a6766342887db01209f28683ab41cdacb9722e092635037d28c350b546c7b6"),
            Map.entry("turntable.svg", "8694124c9027aa4a955788025c00aa6df469cc7f37e3a771b9cf20726277eaba")
    );

    private final FileService fileService;

    @Override
    public String getKey() {
        return "removeBundledCustomSvgIcons";
    }

    @Override
    public String getDescription() {
        return "Remove bundled custom SVG icons that now use Lucide replacements";
    }

    @Override
    public void execute() {
        Path iconDir = Path.of(fileService.getIconsSvgFolder());
        if (!Files.isDirectory(iconDir)) {
            log.info("Bundled custom SVG cleanup skipped; icon directory does not exist: {}", iconDir);
            return;
        }

        int deletedCount = 0;
        for (Map.Entry<String, String> bundledIcon : BUNDLED_ICON_CHECKSUMS.entrySet()) {
            String filename = bundledIcon.getKey();
            Path iconFile = iconDir.resolve(filename);
            try {
                if (Files.isRegularFile(iconFile) && hasExpectedChecksum(iconFile, bundledIcon.getValue())) {
                    Files.delete(iconFile);
                    deletedCount++;
                }
            } catch (IOException e) {
                log.warn("Failed to remove bundled custom SVG icon '{}'", filename, e);
            }
        }

        log.info("Removed {} bundled custom SVG icons from {}", deletedCount, iconDir);
    }

    private boolean hasExpectedChecksum(Path iconFile, String expectedChecksum) throws IOException {
        return sha256(iconFile).equals(expectedChecksum);
    }

    private String sha256(Path iconFile) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(iconFile));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
