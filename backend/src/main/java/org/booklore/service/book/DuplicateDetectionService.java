package org.booklore.service.book;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.request.DuplicateDetectionRequest;
import org.booklore.model.dto.response.DuplicateGroup;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.service.file.FileMovingHelper;
import org.booklore.util.BookUtils;
import org.booklore.util.PathPatternResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class DuplicateDetectionService {

    private static final List<BookFileType> DEFAULT_FORMAT_PRIORITY = List.of(
            BookFileType.EPUB, BookFileType.PDF, BookFileType.AZW3,
            BookFileType.MOBI, BookFileType.FB2, BookFileType.CBX, BookFileType.AUDIOBOOK
    );
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern UNDERSCORE_HYPHEN_PATTERN = Pattern.compile("[_\\-]");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9\\s]");

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final FileMovingHelper fileMovingHelper;

    @Transactional(readOnly = true)
    public List<DuplicateGroup> findDuplicates(DuplicateDetectionRequest request) {
        List<BookEntity> books = bookRepository.findAllForDuplicateDetection(request.libraryId());
        if (books.size() < 2) {
            return List.of();
        }

        LibraryEntity library = books.getFirst().getLibrary();
        List<BookFileType> formatPriority = library != null
                ? library.getFormatPriority()
                : null;
        if (formatPriority == null || formatPriority.isEmpty()) {
            formatPriority = DEFAULT_FORMAT_PRIORITY;
        }
        String fileNamingPattern = library != null ? fileMovingHelper.getFileNamingPattern(library) : null;
        Comparator<BookEntity> targetComparator = buildTargetComparator(formatPriority, fileNamingPattern);

        Set<Long> alreadyGrouped = new HashSet<>();
        List<DuplicateGroup> groups = new ArrayList<>();

        if (request.matchByIsbn()) {
            groups.addAll(findByIsbn(books, alreadyGrouped, targetComparator));
        }
        if (request.matchByExternalId()) {
            groups.addAll(findByExternalId(books, alreadyGrouped, targetComparator));
        }
        if (request.matchByTitleAuthor()) {
            groups.addAll(findByTitleAuthor(books, alreadyGrouped, targetComparator));
        }
        if (request.matchByDirectory()) {
            groups.addAll(findByDirectory(books, alreadyGrouped, targetComparator));
        }
        if (request.matchByFilename()) {
            groups.addAll(findByFilename(books, alreadyGrouped, targetComparator));
        }

        return groups;
    }

    private List<DuplicateGroup> findByIsbn(List<BookEntity> books, Set<Long> alreadyGrouped, Comparator<BookEntity> targetComparator) {
        Map<String, List<BookEntity>> isbnGroups = new HashMap<>();

        for (BookEntity book : books) {
            if (alreadyGrouped.contains(book.getId())) continue;
            BookMetadataEntity meta = book.getMetadata();
            if (meta == null) continue;

            String isbn13 = meta.getIsbn13();
            if (isbn13 == null && meta.getIsbn10() != null) {
                isbn13 = BookUtils.isbn10To13(meta.getIsbn10());
            }
            if (isbn13 != null && !isbn13.isBlank()) {
                isbnGroups.computeIfAbsent(isbn13.trim(), k -> new ArrayList<>()).add(book);
            }
        }

        return buildGroups(isbnGroups, "ISBN", alreadyGrouped, targetComparator);
    }

    private List<DuplicateGroup> findByExternalId(List<BookEntity> books, Set<Long> alreadyGrouped, Comparator<BookEntity> targetComparator) {
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, BookEntity> bookMap = new HashMap<>();

        List<BookEntity> candidates = books.stream()
                .filter(b -> !alreadyGrouped.contains(b.getId()))
                .filter(b -> b.getMetadata() != null)
                .toList();

        for (BookEntity book : candidates) {
            bookMap.put(book.getId(), book);
            parent.put(book.getId(), book.getId());
        }

        Map<String, Long> idToBook = new HashMap<>();
        for (BookEntity book : candidates) {
            BookMetadataEntity meta = book.getMetadata();
            List<String> externalIds = extractExternalIds(meta);
            for (String extId : externalIds) {
                if (idToBook.containsKey(extId)) {
                    union(parent, book.getId(), idToBook.get(extId));
                } else {
                    idToBook.put(extId, book.getId());
                }
            }
        }

        Map<Long, List<BookEntity>> groups = new HashMap<>();
        for (BookEntity book : candidates) {
            Long root = find(parent, book.getId());
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(book);
        }

        List<DuplicateGroup> result = new ArrayList<>();
        for (List<BookEntity> group : groups.values()) {
            if (group.size() < 2) continue;
            group.forEach(b -> alreadyGrouped.add(b.getId()));
            result.add(toDuplicateGroup(group, "EXTERNAL_ID", targetComparator));
        }
        return result;
    }

    private List<String> extractExternalIds(BookMetadataEntity meta) {
        List<String> ids = new ArrayList<>();
        addIfPresent(ids, "goodreads:", meta.getGoodreadsId());
        addIfPresent(ids, "hardcover:", meta.getHardcoverId());
        addIfPresent(ids, "google:", meta.getGoogleId());
        addIfPresent(ids, "asin:", meta.getAsin());
        addIfPresent(ids, "audible:", meta.getAudibleId());
        addIfPresent(ids, "comicvine:", meta.getComicvineId());
        addIfPresent(ids, "applebooks:", meta.getApplebooksId());
        addIfPresent(ids, "openlibrary:", meta.getOpenlibraryId());
        return ids;
    }

    private void addIfPresent(List<String> list, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            list.add(prefix + value.trim());
        }
    }

    private Long find(Map<Long, Long> parent, Long x) {
        while (!parent.get(x).equals(x)) {
            parent.put(x, parent.get(parent.get(x)));
            x = parent.get(x);
        }
        return x;
    }

    private void union(Map<Long, Long> parent, Long a, Long b) {
        Long rootA = find(parent, a);
        Long rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }

    private List<DuplicateGroup> findByTitleAuthor(List<BookEntity> books, Set<Long> alreadyGrouped, Comparator<BookEntity> targetComparator) {
        Map<String, List<BookEntity>> titleGroups = new HashMap<>();

        for (BookEntity book : books) {
            if (alreadyGrouped.contains(book.getId())) continue;
            BookMetadataEntity meta = book.getMetadata();
            if (meta == null || meta.getTitle() == null || meta.getTitle().isBlank()) continue;

            String normalizedTitle = BookUtils.normalizeForSearch(meta.getTitle());
            if (normalizedTitle != null && !normalizedTitle.isBlank()) {
                titleGroups.computeIfAbsent(normalizedTitle, k -> new ArrayList<>()).add(book);
            }
        }

        List<DuplicateGroup> result = new ArrayList<>();
        for (List<BookEntity> group : titleGroups.values()) {
            if (group.size() < 2) continue;

            List<BookEntity> withAuthors = group.stream()
                    .filter(b -> b.getMetadata().getAuthors() != null && !b.getMetadata().getAuthors().isEmpty())
                    .toList();

            if (withAuthors.size() < 2) continue;

            Map<Long, Set<String>> normalizedAuthors = new HashMap<>();
            for (BookEntity book : withAuthors) {
                Set<String> names = book.getMetadata().getAuthors().stream()
                        .map(AuthorEntity::getName)
                        .filter(Objects::nonNull)
                        .map(BookUtils::normalizeForSearch)
                        .collect(Collectors.toSet());
                normalizedAuthors.put(book.getId(), names);
            }

            Map<Long, Long> parent = new HashMap<>();
            for (BookEntity book : withAuthors) {
                parent.put(book.getId(), book.getId());
            }

            for (int i = 0; i < withAuthors.size(); i++) {
                for (int j = i + 1; j < withAuthors.size(); j++) {
                    Set<String> authorsI = normalizedAuthors.get(withAuthors.get(i).getId());
                    Set<String> authorsJ = normalizedAuthors.get(withAuthors.get(j).getId());
                    boolean overlap = authorsI.stream().anyMatch(authorsJ::contains);
                    if (overlap) {
                        union(parent, withAuthors.get(i).getId(), withAuthors.get(j).getId());
                    }
                }
            }

            Map<Long, List<BookEntity>> subGroups = new HashMap<>();
            for (BookEntity book : withAuthors) {
                Long root = find(parent, book.getId());
                subGroups.computeIfAbsent(root, k -> new ArrayList<>()).add(book);
            }

            for (List<BookEntity> subGroup : subGroups.values()) {
                if (subGroup.size() < 2) continue;
                subGroup.forEach(b -> alreadyGrouped.add(b.getId()));
                result.add(toDuplicateGroup(subGroup, "TITLE_AUTHOR", targetComparator));
            }
        }

        return result;
    }

    private List<DuplicateGroup> findByDirectory(List<BookEntity> books, Set<Long> alreadyGrouped, Comparator<BookEntity> targetComparator) {
        Map<String, List<BookEntity>> dirGroups = new HashMap<>();

        for (BookEntity book : books) {
            if (alreadyGrouped.contains(book.getId())) continue;
            if (book.getLibraryPath() == null) continue;

            BookFileEntity primary = book.getPrimaryBookFile();

            if (primary == null) continue;

            String subPath = primary.getFileSubPath();
            if (subPath == null || subPath.isBlank()) continue;

            String key = book.getLibraryPath().getId() + ":" + subPath;
            dirGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(book);
        }

        return buildGroups(dirGroups, "DIRECTORY", alreadyGrouped, targetComparator);
    }

    private List<DuplicateGroup> findByFilename(List<BookEntity> books, Set<Long> alreadyGrouped, Comparator<BookEntity> targetComparator) {
        Map<String, List<BookEntity>> nameGroups = new HashMap<>();

        for (BookEntity book : books) {
            if (alreadyGrouped.contains(book.getId())) continue;

            BookFileEntity primary = book.getPrimaryBookFile();
            if (primary == null || primary.getFileName() == null) continue;

            String fileName = primary.getFileName();
            int dotIdx = fileName.lastIndexOf('.');
            String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;

            String normalized = baseName.toLowerCase();
            normalized = UNDERSCORE_HYPHEN_PATTERN.matcher(normalized).replaceAll(" ");
            normalized = NON_ALPHANUMERIC_PATTERN.matcher(normalized).replaceAll("");
            normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();

            if (!normalized.isBlank()) {
                nameGroups.computeIfAbsent(normalized, k -> new ArrayList<>()).add(book);
            }
        }

        return buildGroups(nameGroups, "FILENAME", alreadyGrouped, targetComparator);
    }

    private List<DuplicateGroup> buildGroups(Map<String, List<BookEntity>> groupMap, String reason,
                                              Set<Long> alreadyGrouped, Comparator<BookEntity> targetComparator) {
        List<DuplicateGroup> result = new ArrayList<>();
        for (List<BookEntity> group : groupMap.values()) {
            if (group.size() < 2) continue;
            group.forEach(b -> alreadyGrouped.add(b.getId()));
            result.add(toDuplicateGroup(group, reason, targetComparator));
        }
        return result;
    }

    private DuplicateGroup toDuplicateGroup(List<BookEntity> entities, String reason, Comparator<BookEntity> targetComparator) {
        BookEntity suggested = entities.stream()
                .max(targetComparator)
                .orElse(entities.getFirst());

        List<Book> books = entities.stream()
                .map(bookMapper::toBook)
                .toList();

        return new DuplicateGroup(suggested.getId(), reason, books);
    }

    private Comparator<BookEntity> buildTargetComparator(List<BookFileType> formatPriority, String fileNamingPattern) {
        return Comparator
                .comparingInt((BookEntity b) -> formatPriorityScore(b, formatPriority))
                .thenComparingLong(b -> b.getBookFiles() == null ? 0 :
                        b.getBookFiles().stream().filter(BookFileEntity::isBookFormat).count())
                .thenComparing(b -> b.getMetadataMatchScore() != null ? b.getMetadataMatchScore() : 0f)
                .thenComparingInt(b -> patternMatchScore(b, fileNamingPattern))
                .thenComparing(BookEntity::getId, Comparator.reverseOrder());
    }

    private int patternMatchScore(BookEntity book, String fileNamingPattern) {
        if (fileNamingPattern == null || fileNamingPattern.isBlank()) {
            return 0;
        }
        BookFileEntity primary = book.getPrimaryBookFile();
        if (primary == null || primary.getFileName() == null) {
            return 0;
        }

        String expected = PathPatternResolver.resolvePattern(book, primary, fileNamingPattern);
        String actualDir = primary.getFileSubPath() != null ? primary.getFileSubPath().replace('\\', '/') : "";
        String actualPath = actualDir.isEmpty() ? primary.getFileName() : actualDir + "/" + primary.getFileName();
        if (actualPath.equals(expected)) {
            return 2;
        }

        int lastSlash = expected.lastIndexOf('/');
        String expectedDir = lastSlash >= 0 ? expected.substring(0, lastSlash) : "";
        return !expectedDir.isEmpty() && actualDir.equals(expectedDir) ? 1 : 0;
    }

    private int formatPriorityScore(BookEntity book, List<BookFileType> formatPriority) {
        if (book.getBookFiles() == null) {
            return 0;
        }
        int bestScore = 0;
        for (BookFileEntity file : book.getBookFiles()) {
            if (!file.isBookFormat() || file.getBookType() == null) continue;
            int index = formatPriority.indexOf(file.getBookType());
            if (index >= 0) {
                int score = formatPriority.size() - index;
                bestScore = Math.max(bestScore, score);
            }
        }
        return bestScore;
    }
}
