package org.grimmory.service.book;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.exception.ApiError;
import org.grimmory.mapper.BookReviewMapper;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.dto.BookReview;
import org.grimmory.model.dto.settings.MetadataPublicReviewsSettings;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.enums.MetadataProvider;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.BookReviewRepository;
import org.grimmory.service.appsettings.AppSettingService;
import org.grimmory.service.metadata.BookReviewUpdateService;
import org.grimmory.service.metadata.MetadataRefreshService;
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