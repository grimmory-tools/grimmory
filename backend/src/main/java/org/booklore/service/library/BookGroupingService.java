package org.booklore.service.library;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.repository.BookRepository;
import org.booklore.util.BookFileGroupingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookGroupingService {

    private static final double FILELESS_MATCH_THRESHOLD = 0.85;

    private final BookRepository bookRepository;

    public record GroupingResult(
            Map<BookEntity, List<LibraryFile>> filesToAttach,
            Map<String, List<LibraryFile>> newBookGroups
    ) {}

    public Map<String, List<LibraryFile>> groupForInitialScan(List<LibraryFile> newFiles, LibraryEntity libraryEntity) {
        return group(newFiles, libraryEntity.getOrganizationMode());
    }

    public GroupingResult groupForRescan(List<LibraryFile> newFiles, LibraryEntity libraryEntity) {
        Map<BookEntity, List<LibraryFile>> filesToAttach = new LinkedHashMap<>();
        List<LibraryFile> unmatched = new ArrayList<>();

        for (LibraryFile file : newFiles) {
            BookEntity match = findMatchingBook(file, libraryEntity.getOrganizationMode());
            if (match != null) {
                filesToAttach.computeIfAbsent(match, k -> new ArrayList<>()).add(file);
            } else {
                unmatched.add(file);
            }
        }

        Map<String, List<LibraryFile>> newBookGroups = group(unmatched, libraryEntity.getOrganizationMode());

        return new GroupingResult(filesToAttach, newBookGroups);
    }

    public Map<String, List<LibraryFile>> group(List<LibraryFile> newFiles, LibraryOrganizationMode mode) {
        if (newFiles.isEmpty()) {
            log.debug("No books for grouping, skipping.");
            return Collections.emptyMap();
        }

        if (mode == LibraryOrganizationMode.BOOK_PER_FILE) {
            return groupByFile(newFiles);
        } else if (mode == LibraryOrganizationMode.BOOK_PER_FOLDER) {
            return groupByFolder(newFiles);
        } else {
            log.warn("Unknown mode during book grouping: {}", mode);
            return Collections.emptyMap();
        }
    }

    private Map<String, List<LibraryFile>> groupByFile(List<LibraryFile> files) {
        Map<String, List<LibraryFile>> result = new LinkedHashMap<>();

        for (LibraryFile file : files) {
            String key = file.getLibraryPathEntity().getId() + ":" +
                    (file.getFileSubPath() == null ? "" : file.getFileSubPath()) + ":" +
                    file.getFileName();
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
        }

        log.debug("BOOK_PER_FILE grouping: {} files into {} groups", files.size(), result.size());
        return result;
    }

    private Map<String, List<LibraryFile>> groupByFolder(List<LibraryFile> files) {
        Map<String, List<LibraryFile>> result = new LinkedHashMap<>();

        Set<String> ebookFolders = new HashSet<>();
        for (LibraryFile file : files) {
            if (file.getBookFileType() != BookFileType.AUDIOBOOK) {
                String folderKey = file.getLibraryPathEntity().getId() + ":" +
                        (file.getFileSubPath() == null ? "" : file.getFileSubPath());
                ebookFolders.add(folderKey);
            }
        }

        for (LibraryFile file : files) {
            Long pathId = file.getLibraryPathEntity().getId();
            String subPath = file.getFileSubPath() == null ? "" : file.getFileSubPath();

            if (subPath.isEmpty()) {
                String key = pathId + "::" + file.getFileName();
                result.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
            } else if (file.getBookFileType() == BookFileType.AUDIOBOOK) {
                // Folder-based audiobook entries represent a collapsed directory (e.g., subPath="Author",
                // fileName="audiobook-folder"). Their subPath points to the parent because getRelativeSubPath
                // takes .getParent() (designed for files, not directories). Reconstruct the actual folder path
                // so sibling audiobook folders stay separate and absorption searches from the correct level.
                String effectiveSubPath = file.isFolderBased()
                        ? subPath + "/" + file.getFileName()
                        : subPath;
                String ancestorKey = findNearestEbookAncestor(pathId, effectiveSubPath, ebookFolders);
                if (ancestorKey != null) {
                    result.computeIfAbsent(ancestorKey, k -> new ArrayList<>()).add(file);
                } else {
                    String key = pathId + ":" + effectiveSubPath;
                    result.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
                }
            } else {
                String key = pathId + ":" + subPath;
                result.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
            }
        }

        log.debug("BOOK_PER_FOLDER grouping: {} files into {} groups", files.size(), result.size());
        return result;
    }

    private String findNearestEbookAncestor(Long pathId, String subPath, Set<String> ebookFolders) {
        String current = subPath;
        while (true) {
            int lastSep = current.lastIndexOf('/');
            if (lastSep == -1) {
                lastSep = current.lastIndexOf('\\');
            }
            if (lastSep <= 0) {
                break;
            }
            current = current.substring(0, lastSep);
            String candidateKey = pathId + ":" + current;
            if (ebookFolders.contains(candidateKey)) {
                return candidateKey;
            }
        }
        return null;
    }

    private BookEntity findMatchingBook(LibraryFile file, LibraryOrganizationMode mode) {
        BookEntity filelessMatch = switch (mode) {
            case BOOK_PER_FILE, BOOK_PER_FOLDER -> findExactFilelessMatch(file, file.getLibraryEntity());
            default -> null;
        };

        if (filelessMatch != null) {
            return filelessMatch;
        }

        if (mode != LibraryOrganizationMode.BOOK_PER_FOLDER) {
            return null;
        }

        String fileSubPath = file.getFileSubPath();
        if (fileSubPath == null || fileSubPath.isEmpty()) {
            return null;
        }

        Long libraryPathId = file.getLibraryPathEntity().getId();
        List<BookEntity> booksInDirectory = bookRepository.findAllByLibraryPathIdAndFileSubPath(libraryPathId, fileSubPath);

        List<BookEntity> activeBooksInDirectory = booksInDirectory.stream()
                .filter(book -> book.getDeleted() == null || !book.getDeleted())
                .toList();

        if (activeBooksInDirectory.isEmpty()) {
            if (file.getBookFileType() == BookFileType.AUDIOBOOK) {
                return findNearestAncestorBookWithEbook(libraryPathId, fileSubPath);
            }
            return null;
        }

        return findMatchBookPerFolderWithAbsorption(file, activeBooksInDirectory);
    }

    private BookEntity findExactFilelessMatch(LibraryFile file, LibraryEntity library) {
        List<BookEntity> filelessBooks = bookRepository.findFilelessBooksByLibraryId(library.getId());
        if (filelessBooks.isEmpty()) {
            return null;
        }

        String fileBaseName = BookFileGroupingUtils.extractGroupingKey(file.getFileName());
        Long fileLibraryPathId = file.getLibraryPathEntity().getId();

        for (BookEntity book : filelessBooks) {
            if (book.getLibraryPath() != null && !book.getLibraryPath().getId().equals(fileLibraryPathId)) {
                continue;
            }

            if (book.getMetadata() != null && book.getMetadata().getTitle() != null) {
                String bookTitle = BookFileGroupingUtils.extractGroupingKey(book.getMetadata().getTitle());
                if (fileBaseName.equals(bookTitle)) {
                    log.debug("Exact matched file '{}' to fileless book '{}' (title: {})",
                            file.getFileName(), book.getId(), book.getMetadata().getTitle());
                    return book;
                }
            }
        }
        return null;
    }

    private BookEntity findMatchBookPerFolderWithAbsorption(LibraryFile file, List<BookEntity> booksInDirectory) {
        List<BookEntity> booksWithFiles = booksInDirectory.stream()
                .filter(BookEntity::hasFiles)
                .toList();

        if (booksWithFiles.isEmpty()) {
            return null;
        }

        if (booksWithFiles.size() == 1) {
            return booksWithFiles.getFirst();
        }

        return booksWithFiles.stream()
                .max(Comparator.comparingLong(book -> book.getBookFiles() != null ? book.getBookFiles().size() : 0))
                .orElse(null);
    }

    private BookEntity findNearestAncestorBookWithEbook(Long libraryPathId, String subPath) {
        String current = subPath;
        while (true) {
            int lastSep = current.lastIndexOf('/');
            if (lastSep == -1) {
                lastSep = current.lastIndexOf('\\');
            }
            if (lastSep <= 0) {
                break;
            }
            current = current.substring(0, lastSep);

            List<BookEntity> booksAtLevel = bookRepository.findAllByLibraryPathIdAndFileSubPath(libraryPathId, current);
            for (BookEntity book : booksAtLevel) {
                if (book.getDeleted() != null && book.getDeleted()) {
                    continue;
                }
                if (book.getBookFiles() != null && book.getBookFiles().stream()
                        .anyMatch(bf -> bf.getBookType() != BookFileType.AUDIOBOOK)) {
                    log.debug("BOOK_PER_FOLDER: Audio absorption matched to ancestor book id={} at '{}'",
                            book.getId(), current);
                    return book;
                }
            }
        }
        return null;
    }
}
