package org.booklore.service.book;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookReviewMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookReview;
import org.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.BookRepository;
import org.booklore.repository.BookReviewRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.BookReviewUpdateService;
import org.booklore.service.metadata.MetadataRefreshService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookReviewService {

    private final BookReviewRepository bookReviewRepository;
    private final BookReviewMapper mapper;
    private final BookReviewUpdateService bookReviewUpdateService;
    private final BookRepository bookRepository;
    private final AppSettingService appSettingService;
    private final MetadataRefreshService metadataRefreshService;
    private final AuthenticationService authenticationService;

    public List<BookReview> getByBookId(Long bookId) {
        BookEntity bookEntity = bookRepository.findByIdWithMetadata(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        List<BookReview> existingReviews = bookReviewRepository.findByBookMetadataBookId(bookId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());

        MetadataPublicReviewsSettings reviewSettings = appSettingService.getAppSettings().getMetadataPublicReviewsSettings();

        // Return existing reviews if download is disabled or reviews already exist
        if (!reviewSettings.isDownloadEnabled() || !existingReviews.isEmpty()) {
            return existingReviews;
        }

        // Check user permissions for auto-download
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();
        boolean hasPermission = currentUser.getPermissions().isAdmin() || currentUser.getPermissions().isCanManageLibrary();

        if (!hasPermission || !reviewSettings.isAutoDownloadEnabled()) {
            return existingReviews;
        }

        try {
            List<BookReview> fetchedReviews = fetchBookReviews(bookEntity);
            if (!fetchedReviews.isEmpty()) {
                bookReviewUpdateService.addReviewsToBook(fetchedReviews, bookEntity.getMetadata());
                bookRepository.save(bookEntity);
                return bookReviewRepository.findByBookMetadataBookId(bookId).stream()
                        .map(mapper::toDto)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to auto-fetch reviews for book {}: {}", bookId, e.getMessage());
        }

        return existingReviews;
    }

    public List<BookReview> fetchBookReviews(BookEntity bookEntity) {

        MetadataPublicReviewsSettings settings = appSettingService.getAppSettings().getMetadataPublicReviewsSettings();
        if (!settings.isDownloadEnabled()) {
            return Collections.emptyList();
        }

        List<MetadataProvider> providers = settings.getProviders().stream()
                .filter(MetadataPublicReviewsSettings.ReviewProviderConfig::isEnabled)
                .map(MetadataPublicReviewsSettings.ReviewProviderConfig::getProvider)
                .collect(Collectors.toList());

        Map<MetadataProvider, BookMetadata> metadataMap = metadataRefreshService.fetchMetadataForBook(providers, bookEntity);

        return metadataMap.values().stream()
                .filter(meta -> meta.getBookReviews() != null)
                .flatMap(meta -> meta.getBookReviews().stream())
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(Long id) {
        if (!bookReviewRepository.existsById(id)) {
            throw new EntityNotFoundException("Review not found: " + id);
        }
        bookReviewRepository.deleteById(id);
    }

    @Transactional
    public List<BookReview> refreshReviews(Long bookId) {
        BookEntity bookEntity = bookRepository.findByIdWithMetadata(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        bookEntity.getMetadata().getReviews().clear();
        bookRepository.save(bookEntity);

        bookReviewRepository.deleteByBookMetadataBookId(bookId);

        List<BookReview> freshReviews = fetchBookReviews(bookEntity);
        bookReviewUpdateService.addReviewsToBook(freshReviews, bookEntity.getMetadata());
        bookRepository.save(bookEntity);

        return bookReviewRepository.findByBookMetadataBookId(bookId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAllByBookId(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw ApiError.BOOK_NOT_FOUND.createException(bookId);
        }
        bookReviewRepository.deleteByBookMetadataBookId(bookId);
    }
}