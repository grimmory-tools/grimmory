package org.booklore.service.acquisition;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.acquisition.AcquisitionResult;
import org.booklore.model.dto.acquisition.AddToWantedRequest;
import org.booklore.model.dto.acquisition.JobHistoryDTO;
import org.booklore.model.dto.acquisition.NzbResult;
import org.booklore.model.dto.acquisition.WantedBookDTO;
import org.booklore.model.entity.AcquisitionClientEntity;
import org.booklore.model.entity.AcquisitionIndexerEntity;
import org.booklore.model.entity.AcquisitionJobHistoryEntity;
import org.booklore.model.entity.WantedBookEntity;
import org.booklore.model.enums.JobHistoryStatus;
import org.booklore.model.enums.WantedBookStatus;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.repository.AcquisitionClientRepository;
import org.booklore.repository.AcquisitionIndexerRepository;
import org.booklore.repository.AcquisitionJobHistoryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.WantedBookRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@AllArgsConstructor
public class AcquisitionService {

    private final AcquisitionIndexerRepository indexerRepository;
    private final AcquisitionClientRepository clientRepository;
    private final WantedBookRepository wantedBookRepository;
    private final AcquisitionJobHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final NewznabClient newznabClient;
    private final SabnzbdClient sabnzbdClient;
    private final ConfidenceScorer confidenceScorer;

    @Transactional
    public WantedBookDTO addToWanted(AddToWantedRequest request) {
        if (request.isbn13() != null && !request.isbn13().isBlank()) {
            if (wantedBookRepository.existsByIsbn13(request.isbn13())) {
                throw ApiError.DUPLICATE_WANTED_BOOK.createException(request.isbn13());
            }
        } else if (request.title() != null && request.author() != null) {
            if (wantedBookRepository.existsByTitleIgnoreCaseAndAuthorIgnoreCase(
                    request.title(), request.author())) {
                throw ApiError.DUPLICATE_WANTED_BOOK.createException(request.title());
            }
        }

        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();
        var addedByEntity = userRepository.findById(currentUser.getId()).orElse(null);

        WantedBookEntity entity = WantedBookEntity.builder()
                .title(request.title())
                .author(request.author())
                .isbn13(request.isbn13())
                .isbn10(request.isbn10())
                .provider(request.provider())
                .providerBookId(request.providerBookId())
                .thumbnailUrl(request.thumbnailUrl())
                .status(WantedBookStatus.WANTED)
                .addedAt(Instant.now())
                .addedBy(addedByEntity)
                .build();

        WantedBookEntity saved = wantedBookRepository.save(entity);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<WantedBookDTO> getWantedBooks() {
        return wantedBookRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public void removeWanted(Long id) {
        if (!wantedBookRepository.existsById(id)) {
            throw ApiError.WANTED_BOOK_NOT_FOUND.createException(id);
        }
        wantedBookRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<JobHistoryDTO> getJobHistory(Long wantedBookId) {
        if (!wantedBookRepository.existsById(wantedBookId)) {
            throw ApiError.WANTED_BOOK_NOT_FOUND.createException(wantedBookId);
        }
        return historyRepository.findByWantedBookIdOrderByAttemptedAtDesc(wantedBookId).stream()
                .map(e -> new JobHistoryDTO(
                        e.getId(),
                        e.getWantedBook().getId(),
                        e.getIndexerId(),
                        e.getNzbTitle(),
                        e.getConfidence(),
                        e.getStatus(),
                        e.getAttemptedAt()
                ))
                .toList();
    }

    @Async
    public void triggerSearch(Long wantedBookId) {
        WantedBookEntity wanted = wantedBookRepository.findById(wantedBookId)
                .orElseThrow(() -> ApiError.WANTED_BOOK_NOT_FOUND.createException(wantedBookId));
        if (wanted.getStatus() == WantedBookStatus.SEARCHING || wanted.getStatus() == WantedBookStatus.DOWNLOADING) {
            log.info("Book id={} already in status {}, skipping trigger", wantedBookId, wanted.getStatus());
            return;
        }
        searchAndDispatch(wanted);
    }

    @Transactional
    public AcquisitionResult searchAndDispatch(WantedBookEntity wanted) {
        wanted.setStatus(WantedBookStatus.SEARCHING);
        wanted.setLastCheckedAt(Instant.now());
        wantedBookRepository.save(wanted);

        List<String> queries = buildQueries(wanted);

        List<AcquisitionIndexerEntity> indexers = indexerRepository.findByEnabledTrueOrderByPriorityAsc();
        List<NzbResult> allResults = new ArrayList<>();
        for (AcquisitionIndexerEntity indexer : indexers) {
            for (String query : queries) {
                allResults.addAll(newznabClient.searchBooks(indexer, query));
            }
        }

        record ScoredResult(NzbResult result, int score) {}

        Optional<ScoredResult> best = allResults.stream()
                .map(r -> new ScoredResult(r, confidenceScorer.calculateConfidence(wanted, r)))
                .filter(sr -> confidenceScorer.meetsThreshold(sr.score()))
                .max(Comparator.comparingInt(ScoredResult::score));

        if (best.isPresent()) {
            ScoredResult winner = best.get();
            return dispatchToSabnzbd(wanted, winner.result(), winner.score(), allResults);
        } else {
            markFailed(wanted, allResults);
            return AcquisitionResult.notFound(wanted.getId());
        }
    }

    private List<String> buildQueries(WantedBookEntity wanted) {
        List<String> queries = new ArrayList<>();
        StringBuilder main = new StringBuilder(wanted.getTitle());
        if (wanted.getAuthor() != null && !wanted.getAuthor().isBlank()) {
            main.append(" ").append(wanted.getAuthor());
        }
        queries.add(main.toString());
        if (wanted.getIsbn13() != null && !wanted.getIsbn13().isBlank()) {
            queries.add("isbn:" + wanted.getIsbn13());
        }
        return queries;
    }

    private AcquisitionResult dispatchToSabnzbd(WantedBookEntity wanted, NzbResult winner,
                                                 int score, List<NzbResult> allResults) {
        List<AcquisitionClientEntity> clients = clientRepository.findByEnabledTrue();
        if (clients.isEmpty()) {
            log.warn("No enabled SABnzbd client — cannot dispatch NZB for wanted book id={}", wanted.getId());
            markDispatchFailed(wanted, winner, score);
            return AcquisitionResult.notFound(wanted.getId());
        }

        AcquisitionClientEntity client = clients.get(0);
        try {
            String jobId = sabnzbdClient.sendNzb(client, winner.downloadUrl(), wanted.getTitle());

            wanted.setStatus(WantedBookStatus.DOWNLOADING);
            wanted.setDownloadId(jobId);
            wantedBookRepository.save(wanted);

            saveHistory(wanted, winner, score, JobHistoryStatus.SENT);

            log.info("Dispatched '{}' to SABnzbd job={} (confidence={})", winner.title(), jobId, score);
            return AcquisitionResult.dispatched(wanted.getId(), winner.title(), score, jobId);
        } catch (Exception e) {
            log.error("SABnzbd dispatch failed for wanted book id={}: {}", wanted.getId(), e.getMessage());
            markDispatchFailed(wanted, winner, score);
            return AcquisitionResult.notFound(wanted.getId());
        }
    }

    private void markDispatchFailed(WantedBookEntity wanted, NzbResult winner, int score) {
        wanted.setRetryCount(wanted.getRetryCount() + 1);
        wanted.setStatus(WantedBookStatus.FAILED);
        wantedBookRepository.save(wanted);
        saveHistory(wanted, winner, score, JobHistoryStatus.FAILED);
    }

    private void markFailed(WantedBookEntity wanted, List<NzbResult> allResults) {
        wanted.setRetryCount(wanted.getRetryCount() + 1);
        wanted.setStatus(WantedBookStatus.NOT_FOUND);
        wantedBookRepository.save(wanted);

        allResults.stream()
                .map(r -> new Object[]{r, confidenceScorer.calculateConfidence(wanted, r)})
                .sorted((a, b) -> Integer.compare((int) b[1], (int) a[1]))
                .limit(3)
                .forEach(pair -> saveHistory(wanted, (NzbResult) pair[0], (int) pair[1], JobHistoryStatus.SKIPPED));
    }

    private void saveHistory(WantedBookEntity wanted, NzbResult result,
                             int confidence, JobHistoryStatus status) {
        AcquisitionJobHistoryEntity history = AcquisitionJobHistoryEntity.builder()
                .wantedBook(wanted)
                .nzbTitle(result.title())
                .nzbUrl(result.downloadUrl())
                .confidence(confidence)
                .status(status)
                .attemptedAt(Instant.now())
                .build();
        historyRepository.save(history);
    }

    private WantedBookDTO toDTO(WantedBookEntity e) {
        return new WantedBookDTO(
                e.getId(), e.getTitle(), e.getAuthor(), e.getIsbn13(), e.getIsbn10(),
                e.getProvider(), e.getProviderBookId(), e.getThumbnailUrl(),
                e.getStatus(), e.getLastCheckedAt(), e.getDownloadId(),
                e.getAddedBy() != null ? e.getAddedBy().getId() : null,
                e.getAddedAt()
        );
    }
}
