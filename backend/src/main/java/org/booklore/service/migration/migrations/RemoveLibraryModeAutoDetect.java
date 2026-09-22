package org.booklore.service.migration.migrations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.migration.Migration;
import org.booklore.util.BookCoverUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveLibraryModeAutoDetect implements Migration {

    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;

    @Override
    public String getKey() {
        return "removeLibraryModeAutoDetect";
    }

    @Override
    public String getDescription() {
        return "Converts `AUTO_DETECT` mode libraries to be an expected mode.";
    }

    private boolean hasFolderBasedBooks(long libraryId) {
        return bookRepository.exists(
                (root, _, cb) -> cb.and(
                        cb.equal(root.get("library").get("id"), libraryId),
                        cb.equal(root.join("bookFiles").get("folderBased"), true)
                )
        );
    }

    @Override
    public void execute() {
        log.info("Starting migration: {}", getKey());

        int processedCount = 0;

        var targets = this.libraryRepository.findAll();

        for (var library : targets) {
            if (library.getOrganizationMode() != LibraryOrganizationMode.AUTO_DETECT) {
                continue;
            }

            var mode = hasFolderBasedBooks(library.getId()) ?
                    LibraryOrganizationMode.BOOK_PER_FOLDER :
                    LibraryOrganizationMode.BOOK_PER_FILE;

            library.setOrganizationMode(mode);

            log.info("Migrating Library {} organization mode from AUTO_DETECT to {}", library.getId(), mode);
            processedCount++;
        }

        log.info("Completed migration '{}'. Total libraries processed: {}", getKey(), processedCount);
    }
}

