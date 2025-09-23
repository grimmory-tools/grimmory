package com.adityachandel.booklore.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.adityachandel.booklore.config.security.userdetails.KoreaderUserDetails;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.progress.KoreaderProgress;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.KoreaderUserEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.KoreaderUserRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import com.adityachandel.booklore.repository.UserRepository;

@Slf4j
@AllArgsConstructor
@Service
public class KoreaderService {

    private final UserBookProgressRepository progressRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final KoreaderUserRepository koreaderUserRepository;

    public ResponseEntity<Map<String, String>> authorizeUser() {
        KoreaderUserDetails authDetails = getAuthDetails();
        KoreaderUserEntity koreaderUser = findKoreaderUser(authDetails.getUsername());
        validatePassword(koreaderUser, authDetails);

        log.info("User '{}' authorized", authDetails.getUsername());
        return ResponseEntity.ok(Map.of("username", authDetails.getUsername()));
    }

    public KoreaderProgress getProgress(String bookHash) {
        KoreaderUserDetails authDetails = getAuthDetailsWithSyncCheck();
        BookEntity book = findBookByHash(bookHash);
        UserBookProgressEntity progress = findUserProgress(authDetails.getBookLoreUserId(), book.getId());

        log.info("getProgress: fetched progress='{}' percentage={} for userId={} bookHash={}",
                progress.getKoreaderProgress(), progress.getKoreaderProgressPercent(),
                authDetails.getBookLoreUserId(), bookHash);

        return KoreaderProgress.builder()
                .document(bookHash)
                .progress(progress.getKoreaderProgress())
                .percentage(progress.getKoreaderProgressPercent())
                .device("BookLore")
                .device_id("BookLore")
                .build();
    }

    public void saveProgress(String bookHash, KoreaderProgress koProgress) {
        KoreaderUserDetails authDetails = getAuthDetailsWithSyncCheck();
        BookEntity book = findBookByHash(bookHash);
        BookLoreUserEntity user = findBookLoreUser(authDetails.getBookLoreUserId());

        UserBookProgressEntity userProgress = getOrCreateUserProgress(user, book);
        updateProgressData(userProgress, koProgress);
        progressRepository.save(userProgress);

        log.info("saveProgress: saved progress='{}' percentage={} for userId={} bookHash={}",
                koProgress.getProgress(), koProgress.getPercentage(),
                authDetails.getBookLoreUserId(), bookHash);
    }

    private KoreaderUserDetails getAuthDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof KoreaderUserDetails details)) {
            log.warn("Authentication failed: invalid principal type");
            throw ApiError.GENERIC_UNAUTHORIZED.createException("User not authenticated");
        }
        return details;
    }

    private KoreaderUserDetails getAuthDetailsWithSyncCheck() {
        KoreaderUserDetails authDetails = getAuthDetails();
        ensureSyncEnabled(authDetails);
        return authDetails;
    }

    private KoreaderUserEntity findKoreaderUser(String username) {
        return koreaderUserRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("KOReader user '{}' not found", username);
                    return ApiError.GENERIC_NOT_FOUND.createException("KOReader user not found");
                });
    }

    private void validatePassword(KoreaderUserEntity koreaderUser, KoreaderUserDetails authDetails) {
        if (koreaderUser.getPasswordMD5() == null ||
                !koreaderUser.getPasswordMD5().equalsIgnoreCase(authDetails.getPassword())) {
            log.warn("Password mismatch for user '{}'", authDetails.getUsername());
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Invalid credentials");
        }
    }

    private BookEntity findBookByHash(String bookHash) {
        return bookRepository.findByCurrentHash(bookHash)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Book not found for hash " + bookHash));
    }

    private BookLoreUserEntity findBookLoreUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("User not found with id " + userId));
    }

    private UserBookProgressEntity findUserProgress(long userId, Long bookId) {
        return progressRepository.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("No progress found for user and book"));
    }

    private UserBookProgressEntity getOrCreateUserProgress(BookLoreUserEntity user, BookEntity book) {
        return progressRepository.findByUserIdAndBookId(user.getId(), book.getId())
                .orElseGet(() -> {
                    UserBookProgressEntity newProgress = new UserBookProgressEntity();
                    newProgress.setUser(user);
                    newProgress.setBook(book);
                    return newProgress;
                });
    }

    private void updateProgressData(UserBookProgressEntity userProgress, KoreaderProgress koProgress) {
        userProgress.setKoreaderProgress(koProgress.getProgress());
        userProgress.setKoreaderProgressPercent(koProgress.getPercentage());
        userProgress.setKoreaderDevice(koProgress.getDevice());
        userProgress.setKoreaderDeviceId(koProgress.getDevice_id());
        userProgress.setKoreaderLastSyncTime(Instant.now());
        userProgress.setLastReadTime(Instant.now());

        updateReadStatus(userProgress, koProgress.getPercentage());
    }

    private void updateReadStatus(UserBookProgressEntity userProgress, double percentage) {
        if (percentage >= 99.5) {
            userProgress.setReadStatus(ReadStatus.READ);
        } else if (percentage >= 0.5) {
            userProgress.setReadStatus(ReadStatus.READING);
        }
    }

    private void ensureSyncEnabled(KoreaderUserDetails details) {
        if (!details.isSyncEnabled()) {
            log.warn("Sync is disabled for user '{}'", details.getUsername());
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Sync is disabled for this user");
        }
    }
}
