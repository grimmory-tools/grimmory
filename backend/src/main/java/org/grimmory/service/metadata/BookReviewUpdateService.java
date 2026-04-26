package org.booklore.service.metadata;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookReview;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.BookReviewEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BookReviewUpdateService {

    public void addReviewsToBook(List<BookReview> bookReviews, BookMetadataEntity e) {
        BookMetadata tempMetadata = BookMetadata.builder()
                .bookReviews(bookReviews)
                .build();
        MetadataClearFlags clearFlags = new MetadataClearFlags();
        updateBookReviews(tempMetadata, e, clearFlags, true);
    }

    public void updateBookReviews(BookMetadata metadata, BookMetadataEntity entity, MetadataClearFlags clearFlags, boolean mergeWithExisting) {
        if (Boolean.TRUE.equals(entity.getReviewsLocked())) {
            return;
        }
        if (clearFlags.isReviews()) {
            entity.getReviews().clear();
            return;
        }
        if (!isFieldUpdateAllowed(false, metadata.getBookReviews()) || metadata.getBookReviews() == null) {
            return;
        }
        if (mergeWithExisting) {
            addReviewsToEntity(metadata.getBookReviews(), entity);
        } else {
            replaceReviewsInEntity(metadata.getBookReviews(), entity);
        }

        applyReviewLimitsAndUpdate(entity);
    }

    private void addReviewsToEntity(List<BookReview> reviews, BookMetadataEntity entity) {
        for (var review : reviews) {
            if (review == null || review.getMetadataProvider() == null) continue;
            BookReviewEntity reviewEntity = createReviewEntity(review, entity);
            entity.getReviews().add(reviewEntity);
        }
    }

    private void replaceReviewsInEntity(List<BookReview> reviews, BookMetadataEntity entity) {
        entity.getReviews().clear();
        Set<BookReviewEntity> newReviews = reviews.stream()
                .filter(review -> review != null && review.getMetadataProvider() != null)
                .map(review -> createReviewEntity(review, entity))
                .collect(Collectors.toSet());
        entity.getReviews().addAll(newReviews);
    }

    private BookReviewEntity createReviewEntity(BookReview review, BookMetadataEntity entity) {
        return BookReviewEntity.builder()
                .bookMetadata(entity)
                .metadataProvider(review.getMetadataProvider())
                .reviewerName(review.getReviewerName())
                .title(review.getTitle())
                .rating(review.getRating())
                .date(review.getDate())
                .body(review.getBody())
                .spoiler(review.getSpoiler())
                .followersCount(review.getFollowersCount())
                .textReviewsCount(review.getTextReviewsCount())
                .country(review.getCountry())
                .build();
    }

    private void applyReviewLimitsAndUpdate(BookMetadataEntity entity) {
        Set<BookReviewEntity> limitedReviews = applyReviewLimitsPerProvider(entity.getReviews());
        entity.getReviews().clear();
        entity.getReviews().addAll(limitedReviews);
    }

    private Set<BookReviewEntity> applyReviewLimitsPerProvider(Set<BookReviewEntity> reviews) {
        return reviews.stream()
                .collect(Collectors.groupingBy(BookReviewEntity::getMetadataProvider))
                .entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().stream()
                        .sorted(Comparator.comparing(BookReviewEntity::getDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(5))
                .collect(Collectors.toSet());
    }

    private boolean isFieldUpdateAllowed(Boolean isLocked, Object fieldValue) {
        return (isLocked == null || !isLocked) && fieldValue != null;
    }
}
