package org.grimmory.service.metadata;

import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataMatchWeights;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.book.BookQueryService;
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



        if (isPresent(metadata.getTitle(), metadata.getTitleLocked())) score += weights.getTitle();
        if (isPresent(metadata.getSubtitle(), metadata.getSubtitleLocked())) score += weights.getSubtitle();
        if (isPresent(metadata.getDescription(), metadata.getDescriptionLocked())) score += weights.getDescription();
        if (hasContent(metadata.getAuthors(), metadata.getAuthorsLocked())) score += weights.getAuthors();
        if (isPresent(metadata.getPublisher(), metadata.getPublisherLocked())) score += weights.getPublisher();
        if (metadata.getPublishedDate() != null || Boolean.TRUE.equals(metadata.getPublishedDateLocked())) score += weights.getPublishedDate();
        if (isPresent(metadata.getSeriesName(), metadata.getSeriesNameLocked())) score += weights.getSeriesName();
        if ((metadata.getSeriesNumber() != null && metadata.getSeriesNumber() > 0) || Boolean.TRUE.equals(metadata.getSeriesNumberLocked())) score += weights.getSeriesNumber();
        if ((metadata.getSeriesTotal() != null && metadata.getSeriesTotal() > 0) || Boolean.TRUE.equals(metadata.getSeriesTotalLocked())) score += weights.getSeriesTotal();
        if (isPresent(metadata.getIsbn13(), metadata.getIsbn13Locked())) score += weights.getIsbn13();
        if (isPresent(metadata.getIsbn10(), metadata.getIsbn10Locked())) score += weights.getIsbn10();
        if (isPresent(metadata.getLanguage(), metadata.getLanguageLocked())) score += weights.getLanguage();
        if ((metadata.getPageCount() != null && metadata.getPageCount() > 0) || Boolean.TRUE.equals(metadata.getPageCountLocked())) score += weights.getPageCount();
        if (hasContent(metadata.getCategories(), metadata.getCategoriesLocked())) score += weights.getCategories();
        if (isPositive(metadata.getAmazonRating(), metadata.getAmazonRatingLocked())) score += weights.getAmazonRating();
        if (isPositive(metadata.getAmazonReviewCount(), metadata.getAmazonReviewCountLocked())) score += weights.getAmazonReviewCount();
        if (isPositive(metadata.getGoodreadsRating(), metadata.getGoodreadsRatingLocked())) score += weights.getGoodreadsRating();
        if (isPositive(metadata.getGoodreadsReviewCount(), metadata.getGoodreadsReviewCountLocked())) score += weights.getGoodreadsReviewCount();
        if (isPositive(metadata.getHardcoverRating(), metadata.getHardcoverRatingLocked())) score += weights.getHardcoverRating();
        if (isPositive(metadata.getHardcoverReviewCount(), metadata.getHardcoverReviewCountLocked())) score += weights.getHardcoverReviewCount();
        if (isPositive(metadata.getRanobedbRating(), metadata.getRanobedbRatingLocked())) score += weights.getRanobedbRating();
        if (isPositive(metadata.getLubimyczytacRating(), metadata.getLubimyczytacRatingLocked())) score += weights.getLubimyczytacRating();
        if (isPositive(metadata.getAudibleRating(), metadata.getAudibleRatingLocked())) score += weights.getAudibleRating();
        if (isPositive(metadata.getAudibleReviewCount(), metadata.getAudibleReviewCountLocked())) score += weights.getAudibleReviewCount();
        if (metadata.getCoverUpdatedOn() != null || Boolean.TRUE.equals(metadata.getCoverLocked())) score += weights.getCoverImage();

        return (score / totalWeight) * 100f;
    }

    private boolean isPresent(String value, Boolean locked) {
        return (value != null && !value.isBlank()) || Boolean.TRUE.equals(locked);
    }

    private boolean hasContent(Iterable<?> iterable, Boolean locked) {
        return (iterable != null && iterable.iterator().hasNext()) || Boolean.TRUE.equals(locked);
    }

    private boolean isPositive(Number number, Boolean locked) {
        return (number != null && number.doubleValue() > 0) || Boolean.TRUE.equals(locked);
    }
}
