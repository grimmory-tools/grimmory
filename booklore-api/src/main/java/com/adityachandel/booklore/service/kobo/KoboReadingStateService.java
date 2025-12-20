package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.KoboReadingStateMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.KoboSyncSettings;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingState;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingStateWrapper;
import com.adityachandel.booklore.model.dto.response.kobo.KoboReadingStateResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.KoboReadingStateEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.KoboReadingStateRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.hardcover.HardcoverSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboReadingStateService {

    private static final int STATUS_SYNC_BUFFER_SECONDS = 10;

    private final KoboReadingStateRepository repository;
    private final KoboReadingStateMapper mapper;
    private final UserBookProgressRepository progressRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final KoboSettingsService koboSettingsService;
    private final KoboReadingStateBuilder readingStateBuilder;
    private final HardcoverSyncService hardcoverSyncService;

    @Transactional
    public KoboReadingStateResponse saveReadingState(List<KoboReadingState> readingStates) {
        List<KoboReadingState> koboReadingStates = saveAll(readingStates);

        List<KoboReadingStateResponse.UpdateResult> updateResults = koboReadingStates.stream()
                .map(state -> KoboReadingStateResponse.UpdateResult.builder()
                        .entitlementId(state.getEntitlementId())
                        .currentBookmarkResult(KoboReadingStateResponse.Result.success())
                        .statisticsResult(KoboReadingStateResponse.Result.success())
                        .statusInfoResult(KoboReadingStateResponse.Result.success())
                        .build())
                .collect(Collectors.toList());

        return KoboReadingStateResponse.builder()
                .requestResult("Success")
                .updateResults(updateResults)
                .build();
    }

    private List<KoboReadingState> saveAll(List<KoboReadingState> dtos) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        
        return dtos.stream()
                .map(dto -> {
                    KoboReadingStateEntity entity = repository.findByEntitlementId(dto.getEntitlementId())
                            .map(existing -> {
                                existing.setCurrentBookmarkJson(mapper.toJson(dto.getCurrentBookmark()));
                                existing.setStatisticsJson(mapper.toJson(dto.getStatistics()));
                                existing.setStatusInfoJson(mapper.toJson(dto.getStatusInfo()));
                                existing.setLastModifiedString(mapper.cleanString(String.valueOf(dto.getLastModified())));
                                return existing;
                            })
                            .orElseGet(() -> {
                                KoboReadingStateEntity newEntity = mapper.toEntity(dto);
                                newEntity.setCreated(mapper.cleanString(String.valueOf(dto.getCreated())));
                                return newEntity;
                            });

                    KoboReadingStateEntity savedEntity = repository.save(entity);
                    
                    syncKoboProgressToUserBookProgress(dto, user.getId());
                    
                    return savedEntity;
                })
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteReadingState(Long bookId) {
        repository.findByEntitlementId(String.valueOf(bookId)).ifPresent(repository::delete);
    }

    public KoboReadingStateWrapper getReadingState(String entitlementId) {
        Optional<KoboReadingState> readingState = repository.findByEntitlementId(entitlementId)
                .map(mapper::toDto)
                .or(() -> constructReadingStateFromProgress(entitlementId));
        
        return readingState.map(state -> KoboReadingStateWrapper.builder()
                .readingStates(List.of(state))
                .build()).orElse(null);
    }
    
    private Optional<KoboReadingState> constructReadingStateFromProgress(String entitlementId) {
        try {
            Long bookId = Long.parseLong(entitlementId);
            BookLoreUser user = authenticationService.getAuthenticatedUser();
            
            return progressRepository.findByUserIdAndBookId(user.getId(), bookId)
                    .filter(progress -> progress.getKoboProgressPercent() != null || progress.getKoboLocation() != null)
                    .map(progress -> readingStateBuilder.buildReadingStateFromProgress(entitlementId, progress));
        } catch (NumberFormatException e) {
            log.warn("Invalid entitlement ID format when constructing reading state: {}", entitlementId);
            return Optional.empty();
        }
    }
    
    private void syncKoboProgressToUserBookProgress(KoboReadingState readingState, Long userId) {
        try {
            Long bookId = Long.parseLong(readingState.getEntitlementId());
            
            Optional<BookEntity> bookOpt = bookRepository.findById(bookId);
            if (bookOpt.isEmpty()) {
                log.warn("Book not found for entitlement ID: {}", readingState.getEntitlementId());
                return;
            }
            
            BookEntity book = bookOpt.get();
            Optional<BookLoreUserEntity> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found: {}", userId);
                return;
            }
            
            UserBookProgressEntity progress = progressRepository.findByUserIdAndBookId(userId, bookId)
                    .orElseGet(() -> {
                        UserBookProgressEntity newProgress = new UserBookProgressEntity();
                        newProgress.setUser(userOpt.get());
                        newProgress.setBook(book);
                        return newProgress;
                    });
            
            KoboReadingState.CurrentBookmark bookmark = readingState.getCurrentBookmark();
            if (bookmark != null) {
                if (bookmark.getProgressPercent() != null) {
                    progress.setKoboProgressPercent(bookmark.getProgressPercent().floatValue());
                }
                
                KoboReadingState.CurrentBookmark.Location location = bookmark.getLocation();
                if (location != null) {
                    log.debug("Kobo location data: value={}, type={}, source={} (length={})", 
                            location.getValue(), location.getType(), location.getSource(),
                            location.getSource() != null ? location.getSource().length() : 0);
                    progress.setKoboLocation(location.getValue());
                    progress.setKoboLocationType(location.getType());
                    progress.setKoboLocationSource(location.getSource());
                }
            }
            
            Instant now = Instant.now();
            progress.setKoboProgressReceivedTime(now);
            progress.setLastReadTime(now);
            
            if (progress.getKoboProgressPercent() != null) {
                updateReadStatusFromKoboProgress(progress, now);
            }
            
            progressRepository.save(progress);
            log.debug("Synced Kobo progress: bookId={}, progress={}%", bookId, progress.getKoboProgressPercent());
            
            // Sync progress to Hardcover asynchronously (if enabled for this user)
            hardcoverSyncService.syncProgressToHardcover(book.getId(), progress.getKoboProgressPercent(), userId);
        } catch (NumberFormatException e) {
            log.warn("Invalid entitlement ID format: {}", readingState.getEntitlementId());
        }
    }
    
    private void updateReadStatusFromKoboProgress(UserBookProgressEntity userProgress, Instant now) {
        if (shouldPreserveCurrentStatus(userProgress, now)) {
            return;
        }

        double koboProgressPercent = userProgress.getKoboProgressPercent();

        ReadStatus derivedStatus = deriveStatusFromProgress(koboProgressPercent);
        userProgress.setReadStatus(derivedStatus);

        if (derivedStatus == ReadStatus.READ && userProgress.getDateFinished() == null) {
            userProgress.setDateFinished(Instant.now());
        }
    }

    private boolean shouldPreserveCurrentStatus(UserBookProgressEntity progress, Instant now) {
        Instant statusModifiedTime = progress.getReadStatusModifiedTime();
        if (statusModifiedTime == null) {
            return false;
        }

        Instant statusSentTime = progress.getKoboStatusSentTime();

        boolean hasPendingStatusUpdate = statusSentTime == null || statusModifiedTime.isAfter(statusSentTime);
        if (hasPendingStatusUpdate) {
            return true;
        }

        return now.isBefore(statusSentTime.plusSeconds(STATUS_SYNC_BUFFER_SECONDS));
    }
    
    private ReadStatus deriveStatusFromProgress(double progressPercent) {
        KoboSyncSettings settings = koboSettingsService.getCurrentUserSettings();
        
        float finishedThreshold = settings.getProgressMarkAsFinishedThreshold() != null 
                ? settings.getProgressMarkAsFinishedThreshold() : 99f;
        float readingThreshold = settings.getProgressMarkAsReadingThreshold() != null 
                ? settings.getProgressMarkAsReadingThreshold() : 1f;
        
        if (progressPercent >= finishedThreshold) return ReadStatus.READ;
        if (progressPercent >= readingThreshold) return ReadStatus.READING;
        return ReadStatus.UNREAD;
    }
}
