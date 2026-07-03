package org.booklore.service.migration.migrations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.service.migration.Migration;
import org.booklore.util.FileService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveBundledCustomSvgIconsMigration implements Migration {

    private static final List<String> BUNDLED_ICON_FILENAMES = List.of(
            "atom.svg", "banana.svg", "beef.svg", "brain.svg", "chef-hat.svg",
            "drama.svg", "ferris-wheel.svg", "flame-kindling.svg", "ghost.svg", "hamburger.svg",
            "plane.svg", "rocket.svg", "roller-coaster.svg", "rose.svg", "skull.svg",
            "snail.svg", "swords.svg", "tent-tree.svg", "tree-palm.svg", "turntable.svg"
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
        for (String filename : BUNDLED_ICON_FILENAMES) {
            try {
                if (Files.deleteIfExists(iconDir.resolve(filename))) {
                    deletedCount++;
                }
            } catch (IOException e) {
                log.warn("Failed to remove bundled custom SVG icon '{}'", filename, e);
            }
        }

        log.info("Removed {} bundled custom SVG icons from {}", deletedCount, iconDir);
    }
}
