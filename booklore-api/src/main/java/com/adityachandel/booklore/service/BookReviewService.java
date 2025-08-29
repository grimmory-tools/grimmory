package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookReviewMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.BookReview;
import com.adityachandel.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.BookReviewRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.BookReviewUpdateService;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        List<BookReview> reviews = bookReviewRepository.findByBookMetadataBookId(bookId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());

        if (!appSettingService.getAppSettings().getMetadataPublicReviewsSettings().isDownloadEnabled() || !reviews.isEmpty()) {
            return reviews;
        }

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (!(user.getPermissions().isAdmin() || user.getPermissions().isCanManipulateLibrary())) {
            return reviews;
        }

        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        bookReviewUpdateService.addReviewsToBook(fetchBookReviews(bookEntity), bookEntity.getMetadata());
        bookRepository.save(bookEntity);
        return bookReviewRepository.findByBookMetadataBookId(bookId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
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
        BookEntity bookEntity = bookRepository.findById(bookId)
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