package org.booklore.service.book;

import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.ReplacementDeleteGuardRequest;
import org.booklore.model.dto.response.ReplacementDeleteGuardResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuthenticationService authenticationService;
    private final BookService bookService;
    private final Map<String, Guard> guards = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public ReplacementDeleteGuardResponse create(ReplacementDeleteGuardRequest request) {
        String isbn13 = canonical13(request.isbn());
        if (isbn13 == null || request.predecessorId() == request.successorId()) fail();
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        List<BookEntity> population = visibleBooks(user).stream().filter(ReplacementDeleteGuardService::hasAnyIsbn).toList();
        BookEntity predecessor = find(population, request.predecessorId());
        BookEntity successor = find(population, request.successorId());
        if (population.size() != 2 || predecessor == null || successor == null
                || !Boolean.TRUE.equals(predecessor.getIsPhysical()) || Boolean.TRUE.equals(successor.getIsPhysical())
                || !successor.hasFiles() || !consistentIsbn(predecessor, isbn13) || !consistentIsbn(successor, isbn13)) fail();
        UserBookProgressEntity state = progressRepository.findByUserIdAndBookId(user.getId(), successor.getId()).orElse(null);
        if (!stateMatches(state, request.expectedSuccessorState())) fail();
        String id = UUID.randomUUID().toString();
        guards.put(id, new Guard(user.getId(), predecessor.getId(), successor.getId(), population.stream().map(BookEntity::getId).collect(Collectors.toUnmodifiableSet()), isbn13, request.expectedSuccessorState(), Instant.now().plus(TTL)));
        return new ReplacementDeleteGuardResponse(id);
    }

    @Transactional
    public Map<String, Object> consume(String id) {
        Guard guard = guards.remove(id);
        if (guard == null || guard.expiresAt().isBefore(Instant.now())) fail();
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (!Objects.equals(user.getId(), guard.userId())) fail();
        List<BookEntity> population = visibleBooks(user).stream().filter(ReplacementDeleteGuardService::hasAnyIsbn).toList();
        BookEntity predecessor = find(population, guard.predecessorId());
        BookEntity successor = find(population, guard.successorId());
        UserBookProgressEntity state = progressRepository.findByUserIdAndBookId(user.getId(), guard.successorId()).orElse(null);
        if (population.size() != 2 || !populationIds(population).equals(guard.populationIds()) || predecessor == null || successor == null
                || !Boolean.TRUE.equals(predecessor.getIsPhysical()) || Boolean.TRUE.equals(successor.getIsPhysical())
                || !consistentIsbn(predecessor, guard.isbn13()) || !consistentIsbn(successor, guard.isbn13())
                || !stateMatches(state, guard.expectedState())) fail();
        // This is the existing transactional file/sidecar deletion implementation; the guard is consumed first.
        bookService.deleteBooks(Set.of(predecessor.getId()));
        return Map.of("deletedBookId", predecessor.getId(), "guardId", id, "status", "deleted");
    }

    private List<BookEntity> visibleBooks(BookLoreUser user) {
        Set<Long> libraries = user.getAssignedLibraries().stream().map(Library::getId).collect(Collectors.toSet());
        return bookRepository.findAllFullBooksWithFiles().stream().filter(b -> user.getPermissions().isAdmin() || libraries.contains(b.getLibrary().getId())).toList();
    }
    private static BookEntity find(List<BookEntity> books, long id) { return books.stream().filter(b -> b.getId() == id).findFirst().orElse(null); }
    private static Set<Long> populationIds(List<BookEntity> books) { return books.stream().map(BookEntity::getId).collect(Collectors.toSet()); }
    private static boolean hasAnyIsbn(BookEntity b) { return b.getMetadata() != null && (b.getMetadata().getIsbn13() != null || b.getMetadata().getIsbn10() != null); }
    private static boolean matchesIsbn(BookEntity b, String isbn13) { return b.getMetadata() != null && (isbn13.equals(b.getMetadata().getIsbn13()) || isbn13.equals(to13(b.getMetadata().getIsbn10()))); }
    private static boolean consistentIsbn(BookEntity b, String isbn13) { return matchesIsbn(b, isbn13) && (b.getMetadata().getIsbn13() == null || valid13(b.getMetadata().getIsbn13())) && (b.getMetadata().getIsbn10() == null || valid10(b.getMetadata().getIsbn10())); }
    private static String canonical13(String value) { if (value == null) return null; String v = value.replaceAll("[^0-9Xx]", "").toUpperCase(); if (!ISBN.matcher(v).matches()) return null; return v.length() == 13 && valid13(v) ? v : to13(v); }
    private static String to13(String v) { if (v == null || !valid10(v)) return null; String p = "978" + v.substring(0, 9); int sum = 0; for (int i=0;i<12;i++) sum += (p.charAt(i)-'0') * (i%2==0?1:3); return p + ((10-sum%10)%10); }
    private static boolean valid13(String v) { if (v == null || !v.matches("[0-9]{13}")) return false; int s=0; for(int i=0;i<13;i++) s+=(v.charAt(i)-'0')*(i%2==0?1:3); return s%10==0; }
    private static boolean valid10(String v) { if (v == null || !v.matches("[0-9]{9}[0-9X]")) return false; int s=0; for(int i=0;i<10;i++) s+=(v.charAt(i)=='X'?10:v.charAt(i)-'0')*(10-i); return s%11==0; }
    private static boolean stateMatches(UserBookProgressEntity p, ReplacementDeleteGuardRequest.ReaderState e) { return p != null && p.getReadStatus()==e.status() && Objects.equals(p.getDateFinished(),e.finishedAt()) && Objects.equals(p.getPersonalRating(),e.rating()) && Objects.equals(p.getEpubProgressPercent(),e.progressPercent()) && Objects.equals(p.getEpubProgress(),e.progress()) && Objects.equals(p.getEpubProgressHref(),e.progressHref()); }
    private static void fail() { throw new APIException("Replacement delete guard conflict", HttpStatus.CONFLICT); }
    private record Guard(Long userId, Long predecessorId, Long successorId, Set<Long> populationIds, String isbn13, ReplacementDeleteGuardRequest.ReaderState expectedState, Instant expiresAt) {}
}
