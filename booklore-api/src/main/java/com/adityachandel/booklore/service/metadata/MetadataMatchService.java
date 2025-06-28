package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataMatchWeights;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class MetadataMatchService {

    private final AppSettingService appSettingsService;
    private final BookQueryService bookQueryService;

    public void recalculateAllMatchScores() {
        List<BookEntity> allBooks = bookQueryService.getAllFullBookEntities();
        for (BookEntity book : allBooks) {
            Float score = calculateMatchScore(book);
            book.setMetadataMatchScore(score);
        }
        bookQueryService.saveAll(allBooks);
    }

    public Float calculateMatchScore(BookEntity book) {
        if (book == null || book.getMetadata() == null) return 0f;

        BookMetadataEntity metadata = book.getMetadata();

        AppSettings appSettings = appSettingsService.getAppSettings();
        if (appSettings == null || appSettings.getMetadataMatchWeights() == null) return 0f;

        MetadataMatchWeights weights = appSettings.getMetadataMatchWeights();
        float totalWeight = weights.totalWeight();
        if (totalWeight == 0) return 0f;

        float score = 0f;

        if (isPresent(metadata.getTitle())) score += weights.getTitle();
        if (isPresent(metadata.getSubtitle())) score += weights.getSubtitle();
        if (isPresent(metadata.getDescription())) score += weights.getDescription();
        if (hasContent(metadata.getAuthors())) score += weights.getAuthors();
        if (isPresent(metadata.getPublisher())) score += weights.getPublisher();
        if (metadata.getPublishedDate() != null) score += weights.getPublishedDate();
        if (isPresent(metadata.getSeriesName())) score += weights.getSeriesName();
        if (metadata.getSeriesNumber() != null && metadata.getSeriesNumber() > 0) score += weights.getSeriesNumber();
        if (metadata.getSeriesTotal() != null && metadata.getSeriesTotal() > 0) score += weights.getSeriesTotal();
        if (isPresent(metadata.getIsbn13())) score += weights.getIsbn13();
        if (isPresent(metadata.getIsbn10())) score += weights.getIsbn10();
        if (isPresent(metadata.getLanguage())) score += weights.getLanguage();
        if (metadata.getPageCount() != null && metadata.getPageCount() > 0) score += weights.getPageCount();
        if (hasContent(metadata.getCategories())) score += weights.getCategories();
        if (isPositive(metadata.getAmazonRating())) score += weights.getAmazonRating();
        if (isPositive(metadata.getAmazonReviewCount())) score += weights.getAmazonReviewCount();
        if (isPositive(metadata.getGoodreadsRating())) score += weights.getGoodreadsRating();
        if (isPositive(metadata.getGoodreadsReviewCount())) score += weights.getGoodreadsReviewCount();
        if (isPositive(metadata.getHardcoverRating())) score += weights.getHardcoverRating();
        if (isPositive(metadata.getHardcoverReviewCount())) score += weights.getHardcoverReviewCount();
        if (isPresent(metadata.getThumbnail())) score += weights.getCoverImage();

        return score / totalWeight;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasContent(Iterable<?> iterable) {
        return iterable != null && iterable.iterator().hasNext();
    }

    private boolean isPositive(Number number) {
        return number != null && number.doubleValue() > 0;
    }
}