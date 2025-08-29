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

    private KoreaderUserDetails getAuthDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof KoreaderUserDetails details)) {
            log.warn("Authentication failed: invalid principal type");
            throw ApiError.GENERIC_UNAUTHORIZED.createException("User not authenticated");
        }
        return details;
    }

    public ResponseEntity<Map<String, String>> authorizeUser() {
        KoreaderUserDetails details = getAuthDetails();
        Optional<KoreaderUserEntity> userOpt = koreaderUserRepository.findByUsername(details.getUsername());
        if (userOpt.isEmpty()) {
            log.warn("KOReader user '{}' not found", details.getUsername());
            throw ApiError.GENERIC_NOT_FOUND.createException("KOReader user not found");
        }
        KoreaderUserEntity user = userOpt.get();
        if (user.getPasswordMD5() == null || !user.getPasswordMD5().equalsIgnoreCase(details.getPassword())) {
            log.warn("Password mismatch for user '{}'", details.getUsername());
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Invalid credentials");
        }
        log.info("User '{}' authorized", details.getUsername());
        return ResponseEntity.ok(Map.of("username", details.getUsername()));
    }

    public KoreaderProgress getProgress(String bookHash) {
        KoreaderUserDetails details = getAuthDetails();
        ensureSyncEnabled(details);
        long userId = details.getBookLoreUserId();
        BookEntity book = bookRepository.findByCurrentHash(bookHash)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Book not found for hash " + bookHash));
        UserBookProgressEntity progress = progressRepository.findByUserIdAndBookId(userId, book.getId())
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("No progress found for user and book"));
        log.info("getProgress: fetched progress='{}' percentage={} for userId={} bookHash={}", progress.getKoreaderProgress(), progress.getKoreaderProgressPercent(), userId, bookHash);
        return KoreaderProgress.builder()
                .document(bookHash)
                .progress(progress.getKoreaderProgress())
                .percentage(progress.getKoreaderProgressPercent())
                .device("BookLore")
                .device_id("BookLore")
                .build();
    }

    public void saveProgress(String bookHash, KoreaderProgress progressDto) {
        KoreaderUserDetails details = getAuthDetails();
        ensureSyncEnabled(details);
        long userId = details.getBookLoreUserId();
        BookEntity book = bookRepository.findByCurrentHash(bookHash)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Book not found for hash " + bookHash));
        BookLoreUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("User not found with id " + userId));
        UserBookProgressEntity progress = progressRepository.findByUserIdAndBookId(userId, book.getId())
                .orElseGet(() -> {
                    UserBookProgressEntity p = new UserBookProgressEntity();
                    p.setUser(user);
                    p.setBook(book);
                    return p;
                });
        progress.setKoreaderProgress(progressDto.getProgress());
        progress.setKoreaderProgressPercent(progressDto.getPercentage());
        progress.setKoreaderDevice(progressDto.getDevice());
        progress.setKoreaderDeviceId(progressDto.getDevice_id());
        progress.setKoreaderLastSyncTime(Instant.now());
        if (progressDto.getPercentage() >= 0.5) progress.setReadStatus(ReadStatus.READING);
        progress.setLastReadTime(Instant.now());
        progressRepository.save(progress);
        log.info("saveProgress: saved progress='{}' percentage={} for userId={} bookHash={}", progressDto.getProgress(), progressDto.getPercentage(), userId, bookHash);
    }

    private void ensureSyncEnabled(KoreaderUserDetails details) {
        if (!details.isSyncEnabled()) {
            log.warn("Sync is disabled for user '{}'", details.getUsername());
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Sync is disabled for this user");
        }
    }
}
