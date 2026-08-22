package org.booklore.service.book;

import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.ReplacementDeleteGuardRequest;
import org.booklore.model.dto.response.BookDeletionResponse;
import org.booklore.model.dto.response.ReplacementDeleteGuardResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserContentRestrictionRepository;
import org.booklore.security.policy.ContentRestrictionSpecification;
import org.booklore.app.specification.AppBookSpecification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReplacementDeleteGuardService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Pattern ISBN = Pattern.compile("[0-9]{9}[0-9X]|[0-9]{13}");
    private final BookRepository bookRepository;
    private final UserBookProgressRepository progressRepository;
    private final UserBookFileProgressRepository fileProgressRepository;
    private final UserContentRestrictionRepository restrictionRepository;
    private final AuthenticationService authenticationService;
    private final BookService bookService;
    // JVM-local by design: restart or another node invalidates outstanding guards fail-closed; use sticky routing until durable shared storage exists.
    private final Map<String, Guard> guards = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public ReplacementDeleteGuardResponse create(ReplacementDeleteGuardRequest request) {
        purgeExpiredGuards();
        String isbn13 = canonical13(request.isbn());
        if (isbn13 == null || request.predecessorId() == request.successorId()) fail();
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        List<BookEntity> population = matchingPopulation(visibleBooks(user), isbn13);
        BookEntity predecessor = find(population, request.predecessorId());
        BookEntity successor = find(population, request.successorId());
        if (population.size() != 2 || predecessor == null || successor == null
                || !Boolean.TRUE.equals(predecessor.getIsPhysical()) || predecessor.hasFiles() || Boolean.TRUE.equals(successor.getIsPhysical())
                || !successor.hasFiles() || !consistentIsbn(predecessor, isbn13) || !consistentIsbn(successor, isbn13)) fail();
        if (!successorFileProgressEmpty(user.getId(), successor.getId())) fail();
        UserBookProgressEntity state = progressRepository.findByUserIdAndBookId(user.getId(), successor.getId()).orElse(null);
        if (!stateMatches(state, successor, user.getId(), request.expectedSuccessorState())) fail();
        String id = UUID.randomUUID().toString();
        guards.put(id, new Guard(user.getId(), predecessor.getId(), successor.getId(), population.stream().map(BookEntity::getId).collect(Collectors.toUnmodifiableSet()), isbn13, request.expectedSuccessorState(), Instant.now().plus(TTL)));
        return new ReplacementDeleteGuardResponse(id);
    }

    // MariaDB/InnoDB SERIALIZABLE turns the population read into a locking read and
    // holds its row/gap locks until the joined BookService deletion commits. This is
    // the database linearization boundary: ISBN population inserts/updates cannot
    // pass the final validation and deletion as a read-committed race could.
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> consume(String id) {
        purgeExpiredGuards();
        Guard guard = guards.get(id);
        if (guard == null || guard.expiresAt().isBefore(Instant.now())) fail();
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (!Objects.equals(user.getId(), guard.userId())) fail();
        if (!guards.remove(id, guard)) fail();
        List<BookEntity> population = matchingPopulation(visibleBooks(user), guard.isbn13());
        BookEntity predecessor = find(population, guard.predecessorId());
        BookEntity successor = find(population, guard.successorId());
        UserBookProgressEntity state = progressRepository.findByUserIdAndBookId(user.getId(), guard.successorId()).orElse(null);
        if (population.size() != 2 || !populationIds(population).equals(guard.populationIds()) || predecessor == null || successor == null
                || !Boolean.TRUE.equals(predecessor.getIsPhysical()) || predecessor.hasFiles() || Boolean.TRUE.equals(successor.getIsPhysical())
                || !successor.hasFiles() || !consistentIsbn(predecessor, guard.isbn13()) || !consistentIsbn(successor, guard.isbn13())
                || !stateMatches(state, successor, user.getId(), guard.expectedState())
                || !successorFileProgressEmpty(user.getId(), guard.successorId())) fail();
        // The guard is consumed first. If deletion reports an unexpected result, the
        // database transaction rolls back, but filesystem side effects may already exist;
        // report indeterminate rather than claiming that the predecessor survived.
        ResponseEntity<BookDeletionResponse> deletionResult;
        try {
            deletionResult = bookService.deleteBooks(Set.of(predecessor.getId()));
        } catch (RuntimeException e) {
            throw indeterminateDelete();
        }
        BookDeletionResponse deletion = deletionResult == null ? null : deletionResult.getBody();
        if (deletionResult == null || deletionResult.getStatusCode() != HttpStatus.OK || deletion == null
                || !Set.of(predecessor.getId()).equals(deletion.getDeleted())
                || deletion.getFailedFileDeletions() == null || !deletion.getFailedFileDeletions().isEmpty()) {
            throw indeterminateDelete();
        }
        return Map.of("deletedBookId", predecessor.getId(), "guardId", id, "status", "deleted");
    }

    private List<BookEntity> visibleBooks(BookLoreUser user) {
        Set<Long> libraries = user.getAssignedLibraries().stream().map(Library::getId).collect(Collectors.toSet());
        if (user.getPermissions().isAdmin()) return bookRepository.findAllFullBooksWithFiles();
        return bookRepository.findAll(AppBookSpecification.notDeleted()
                .and(inLibraries(libraries))
                .and(ContentRestrictionSpecification.from(restrictionRepository.findByUserId(user.getId()))));
    }
    private static org.springframework.data.jpa.domain.Specification<BookEntity> inLibraries(Set<Long> libraryIds) {
        return (root, query, cb) -> libraryIds.isEmpty()
                ? cb.disjunction() : root.get("library").get("id").in(libraryIds);
    }
    private boolean successorFileProgressEmpty(Long userId, Long bookId) {
        return fileProgressRepository.findByUserIdAndBookFileBookId(userId, bookId).isEmpty();
    }
    private static BookEntity find(List<BookEntity> books, long id) { return books.stream().filter(b -> b.getId() == id).findFirst().orElse(null); }
    private static Set<Long> populationIds(List<BookEntity> books) { return books.stream().map(BookEntity::getId).collect(Collectors.toSet()); }
    static List<BookEntity> matchingPopulation(List<BookEntity> books, String isbn13) { return books.stream().filter(book -> mentionsIsbn(book, isbn13)).toList(); }
    static boolean mentionsIsbn(BookEntity b, String isbn13) {
        if (b.getMetadata() == null) return false;
        return isbn13.equals(canonical13(b.getMetadata().getIsbn13())) || isbn13.equals(canonical13(b.getMetadata().getIsbn10()));
    }
    static boolean consistentIsbn(BookEntity b, String isbn13) {
        if (!mentionsIsbn(b, isbn13)) return false;
        String isbn13Value = b.getMetadata().getIsbn13();
        String isbn10Value = b.getMetadata().getIsbn10();
        return (isbn13Value == null || isbn13.equals(canonical13(isbn13Value)))
                && (isbn10Value == null || isbn13.equals(canonical13(isbn10Value)));
    }
    private static String canonical13(String value) { if (value == null) return null; String v = value.trim().replaceAll("[\\s-]", "").toUpperCase(); if (!ISBN.matcher(v).matches()) return null; return v.length() == 13 && valid13(v) ? v : to13(v); }
    private static String to13(String v) { if (v == null || !valid10(v)) return null; String p = "978" + v.substring(0, 9); int sum = 0; for (int i=0;i<12;i++) sum += (p.charAt(i)-'0') * (i%2==0?1:3); return p + ((10-sum%10)%10); }
    private static boolean valid13(String v) { if (v == null || !v.matches("[0-9]{13}")) return false; int s=0; for(int i=0;i<13;i++) s+=(v.charAt(i)-'0')*(i%2==0?1:3); return s%10==0; }
    private static boolean valid10(String v) { if (v == null || !v.matches("[0-9]{9}[0-9X]")) return false; int s=0; for(int i=0;i<10;i++) s+=(v.charAt(i)=='X'?10:v.charAt(i)-'0')*(10-i); return s%11==0; }
    private static boolean stateMatches(UserBookProgressEntity p, BookEntity successor, Long authenticatedUserId,
                                        ReplacementDeleteGuardRequest.ReaderState e) {
        return p != null && Objects.equals(shelves(successor, authenticatedUserId), e.shelfIds())
                && p.getReadStatus() == e.status() && Objects.equals(p.getDateFinished(), e.finishedAt())
                && Objects.equals(p.getPersonalRating(), e.rating()) && Objects.equals(p.getEpubProgressPercent(), e.progressPercent())
                && Objects.equals(p.getEpubProgress(), e.progress()) && Objects.equals(p.getEpubProgressHref(), e.progressHref())
                && supportedProgress(p);
    }
    static Set<Long> shelves(BookEntity book, Long authenticatedUserId) {
        return book.getShelves() == null ? Set.of() : book.getShelves().stream()
                .filter(shelf -> shelf.isPublic() || (shelf.getUser() != null && authenticatedUserId.equals(shelf.getUser().getId())))
                .map(ShelfEntity::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
    static boolean supportedProgress(UserBookProgressEntity p) {
        return p.getPdfProgress() == null && p.getPdfProgressPercent() == null && p.getCbxProgress() == null
                && p.getCbxProgressPercent() == null && p.getKoreaderProgress() == null && p.getKoreaderProgressPercent() == null
                && p.getKoboProgressPercent() == null && p.getKoboLocation() == null && p.getKoboLocationType() == null
                && p.getKoboLocationSource() == null;
    }
    private void purgeExpiredGuards() { Instant now = Instant.now(); guards.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now)); }
    private static void fail() { throw new APIException("Replacement delete guard conflict", HttpStatus.CONFLICT); }
    private static APIException indeterminateDelete() {
        return new APIException("Replacement delete outcome is indeterminate; predecessor state must be re-read", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    private record Guard(Long userId, Long predecessorId, Long successorId, Set<Long> populationIds, String isbn13, ReplacementDeleteGuardRequest.ReaderState expectedState, Instant expiresAt) {}
}
