package org.booklore.service.kobo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookloreSyncToken;
import org.booklore.model.dto.kobo.*;
import org.booklore.model.entity.KoboLibrarySnapshotEntity;
import org.booklore.model.entity.KoboSnapshotBookEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.KoboDeletedBookProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.util.RequestUtils;
import org.booklore.util.kobo.BookloreSyncTokenGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class KoboLibrarySyncService {

    private final BookloreSyncTokenGenerator tokenGenerator;
    private final KoboLibrarySnapshotService koboLibrarySnapshotService;
    private final KoboEntitlementService entitlementService;
    private final KoboDeletedBookProgressRepository koboDeletedBookProgressRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final KoboServerProxy koboServerProxy;
    private final ObjectMapper objectMapper;
    private final KoboSettingsService koboSettingsService;

    private Collection<Entitlement> getEntitlementsFromKoboStoreResponse(ResponseEntity<JsonNode> koboStoreResponse) {
         return Optional.ofNullable(koboStoreResponse.getBody())
                .map(body -> {
                    try {
                        List<Entitlement> results = new ArrayList<>();
                        if (body.isArray()) {
                            for (JsonNode node : body) {
                                if (node.has("NewEntitlement")) {
                                    results.add(objectMapper.treeToValue(node, NewEntitlement.class));
                                } else if (node.has("ChangedEntitlement")) {
                                    results.add(objectMapper.treeToValue(node, ChangedEntitlement.class));
                                } else {
                                    log.warn("Unknown entitlement type in Kobo response: {}", node);
                                }
                            }
                        }
                        return results;
                    } catch (Exception e) {
                        log.error("Failed to map Kobo response to Entitlement objects", e);
                        return Collections.<Entitlement>emptyList();
                    }
                })
                .orElse(Collections.emptyList());
    }

    @Transactional
    public ResponseEntity<?> syncLibrary(BookLoreUser user, String token) {
        HttpServletRequest request = RequestUtils.getCurrentRequest();
        BookloreSyncToken syncToken = Optional.ofNullable(tokenGenerator.fromRequestHeaders(request)).orElse(new BookloreSyncToken());

        KoboLibrarySnapshotEntity currSnapshot = koboLibrarySnapshotService.findByIdAndUserId(syncToken.getOngoingSyncPointId(), user.getId()).orElseGet(() -> koboLibrarySnapshotService.create(user.getId()));
        Optional<KoboLibrarySnapshotEntity> prevSnapshot = koboLibrarySnapshotService.findByIdAndUserId(syncToken.getLastSuccessfulSyncPointId(), user.getId());

        List<Entitlement> entitlements = new ArrayList<>();
        boolean shouldContinueSync = false;

        if (prevSnapshot.isPresent()) {
            int maxRemaining = 5;
            List<KoboSnapshotBookEntity> removedAll = new ArrayList<>();
            List<KoboSnapshotBookEntity> changedAll = new ArrayList<>();

            koboLibrarySnapshotService.updateSyncedStatusForExistingBooks(prevSnapshot.get().getId(), currSnapshot.getId());

            Page<KoboSnapshotBookEntity> addedPage = koboLibrarySnapshotService.getNewlyAddedBooks(prevSnapshot.get().getId(), currSnapshot.getId(), PageRequest.of(0, maxRemaining), user.getId());
            List<KoboSnapshotBookEntity> addedAll = new ArrayList<>(addedPage.getContent());
            maxRemaining -= addedPage.getNumberOfElements();
            shouldContinueSync = addedPage.hasNext();

            Page<KoboSnapshotBookEntity> changedPage = Page.empty();
            if (addedPage.isLast() && maxRemaining > 0) {
                changedPage = koboLibrarySnapshotService.getChangedBooks(prevSnapshot.get().getId(), currSnapshot.getId(), PageRequest.of(0, maxRemaining));
                changedAll.addAll(changedPage.getContent());
                maxRemaining -= changedPage.getNumberOfElements();
                shouldContinueSync = shouldContinueSync || changedPage.hasNext();
            }

            Page<KoboSnapshotBookEntity> removedPage = Page.empty();
            if (changedPage.isLast() && maxRemaining > 0) {
                removedPage = koboLibrarySnapshotService.getRemovedBooks(prevSnapshot.get().getId(), currSnapshot.getId(), user.getId(), PageRequest.of(0, maxRemaining));
                removedAll.addAll(removedPage.getContent());
                shouldContinueSync = shouldContinueSync || removedPage.hasNext();
            }

            Set<Long> addedIds = addedAll.stream().map(KoboSnapshotBookEntity::getBookId).collect(Collectors.toSet());
            Set<Long> changedIds = changedAll.stream().map(KoboSnapshotBookEntity::getBookId).collect(Collectors.toSet());
            Set<Long> removedIds = removedAll.stream().map(KoboSnapshotBookEntity::getBookId).collect(Collectors.toSet());

            entitlements.addAll(entitlementService.generateNewEntitlements(addedIds, token));
            entitlements.addAll(entitlementService.generateChangedEntitlements(changedIds, token, false));
            entitlements.addAll(entitlementService.generateChangedEntitlements(removedIds, token, true));


            if (!shouldContinueSync) {
                entitlements.addAll(syncReadingStatesToKobo(user.getId(), currSnapshot.getId()));
                entitlements.addAll(entitlementService.generateTags());
            }
        } else {
            int maxRemaining = 5;
            List<KoboSnapshotBookEntity> snapshotBookEntities = new ArrayList<>();
            while (maxRemaining > 0) {
                Page<KoboSnapshotBookEntity> page = koboLibrarySnapshotService.getUnsyncedBooks(currSnapshot.getId(), PageRequest.of(0, maxRemaining));
                snapshotBookEntities.addAll(page.getContent());
                maxRemaining -= page.getNumberOfElements();
                shouldContinueSync = page.hasNext();
                if (!shouldContinueSync || page.getNumberOfElements() == 0) break;
            }
            Set<Long> ids = snapshotBookEntities.stream().map(KoboSnapshotBookEntity::getBookId).collect(Collectors.toSet());
            entitlements.addAll(entitlementService.generateNewEntitlements(ids, token));

            if (!shouldContinueSync) {
                entitlements.addAll(syncReadingStatesToKobo(user.getId(), currSnapshot.getId()));
                entitlements.addAll(entitlementService.generateTags());
            }
        }

        if (!shouldContinueSync) {
            ResponseEntity<JsonNode> koboStoreResponse = null;
            try {
                koboStoreResponse = koboServerProxy.proxyCurrentRequest(null, true);
            } catch (Exception e) {
                log.warn("Failed to get response from Kobo /v1/library/sync, fallback to noproxy", e);
            }

            if (koboStoreResponse != null) {
                entitlements.addAll(getEntitlementsFromKoboStoreResponse(koboStoreResponse));

                String upstreamContinueSyncHeader = koboStoreResponse.getHeaders().getFirst(KoboHeaders.X_KOBO_SYNC);
                String upstreamKoboSyncTokenHeader = koboStoreResponse.getHeaders().getFirst(KoboHeaders.X_KOBO_SYNCTOKEN);

                if (upstreamKoboSyncTokenHeader != null) {
                    syncToken = tokenGenerator.fromBase64(upstreamKoboSyncTokenHeader);
                }

                shouldContinueSync = "continue".equalsIgnoreCase(upstreamContinueSyncHeader);
            }
        }

        if (shouldContinueSync) {
            syncToken.setOngoingSyncPointId(currSnapshot.getId());
        } else {
            prevSnapshot.ifPresent(sp -> koboLibrarySnapshotService.deleteById(sp.getId()));
            koboDeletedBookProgressRepository.deleteBySnapshotIdAndUserId(syncToken.getOngoingSyncPointId(), user.getId());
            syncToken.setOngoingSyncPointId(null);
            syncToken.setLastSuccessfulSyncPointId(currSnapshot.getId());
        }

        return ResponseEntity.ok()
                .header(KoboHeaders.X_KOBO_SYNC, shouldContinueSync ? "continue" : "")
                .header(KoboHeaders.X_KOBO_SYNCTOKEN, tokenGenerator.toBase64(syncToken))
                .body(entitlements);
    }

    private List<ChangedReadingState> syncReadingStatesToKobo(Long userId, String snapshotId) {
        List<UserBookProgressEntity> booksNeedingSync =
                userBookProgressRepository.findAllBooksNeedingKoboSync(userId, snapshotId);

        if (!koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync()) {
            booksNeedingSync = booksNeedingSync.stream()
                    .filter(p -> needsStatusSync(p) || needsKoboProgressSync(p))
                    .toList();
        }

        if (booksNeedingSync.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChangedReadingState> changedStates = entitlementService.generateChangedReadingStates(booksNeedingSync);

        Instant sentTime = Instant.now();
        for (UserBookProgressEntity progress : booksNeedingSync) {
            if (needsStatusSync(progress)) {
                progress.setKoboStatusSentTime(sentTime);
            }
            if (needsProgressSync(progress)) {
                progress.setKoboProgressSentTime(sentTime);
            }
        }
        userBookProgressRepository.saveAll(booksNeedingSync);

        log.info("Synced {} reading states to Kobo", changedStates.size());
        return changedStates;
    }

    private boolean needsStatusSync(UserBookProgressEntity progress) {
        Instant modifiedTime = progress.getReadStatusModifiedTime();
        if (modifiedTime == null) {
            return false;
        }
        Instant sentTime = progress.getKoboStatusSentTime();
        return sentTime == null || modifiedTime.isAfter(sentTime);
    }

    private boolean needsKoboProgressSync(UserBookProgressEntity progress) {
        Instant sentTime = progress.getKoboProgressSentTime();
        Instant receivedTime = progress.getKoboProgressReceivedTime();
        return receivedTime != null && (sentTime == null || receivedTime.isAfter(sentTime));
    }

    private boolean needsProgressSync(UserBookProgressEntity progress) {
        if (needsKoboProgressSync(progress)) {
            return true;
        }

        if (koboSettingsService.getCurrentUserSettings().isTwoWayProgressSync()
                && progress.getEpubProgress() != null && progress.getEpubProgressPercent() != null) {
            Instant sentTime = progress.getKoboProgressSentTime();
            Instant lastReadTime = progress.getLastReadTime();
            if (lastReadTime != null && (sentTime == null || lastReadTime.isAfter(sentTime))) {
                return true;
            }
        }

        return false;
    }
}
