package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;

import java.time.LocalDate;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MetadataCopyHelper {

    private final BookMetadataEntity metadata;

    public MetadataCopyHelper(BookMetadataEntity metadata) {
        this.metadata = metadata;
    }

    private boolean isLocked(Boolean lockedFlag) {
        return Boolean.TRUE.equals(lockedFlag);
    }

    public void copyTitle(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getTitleLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getTitle() != null) consumer.accept(metadata.getTitle());
        }
    }

    public void copySubtitle(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getSubtitleLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getSubtitle() != null) consumer.accept(metadata.getSubtitle());
        }
    }

    public void copyPublisher(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getPublisherLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getPublisher() != null) consumer.accept(metadata.getPublisher());
        }
    }

    public void copyPublishedDate(boolean clear, Consumer<LocalDate> consumer) {
        if (!isLocked(metadata.getPublishedDateLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getPublishedDate() != null) consumer.accept(metadata.getPublishedDate());
        }
    }

    public void copyDescription(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getDescriptionLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getDescription() != null) consumer.accept(metadata.getDescription());
        }
    }

    public void copySeriesName(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getSeriesNameLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getSeriesName() != null) consumer.accept(metadata.getSeriesName());
        }
    }

    public void copySeriesNumber(boolean clear, Consumer<Float> consumer) {
        if (!isLocked(metadata.getSeriesNumberLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getSeriesNumber() != null) consumer.accept(metadata.getSeriesNumber());
        }
    }

    public void copySeriesTotal(boolean clear, Consumer<Integer> consumer) {
        if (!isLocked(metadata.getSeriesTotalLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getSeriesTotal() != null) consumer.accept(metadata.getSeriesTotal());
        }
    }

    public void copyIsbn13(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getIsbn13Locked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getIsbn13() != null) consumer.accept(metadata.getIsbn13());
        }
    }

    public void copyIsbn10(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getIsbn10Locked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getIsbn10() != null) consumer.accept(metadata.getIsbn10());
        }
    }

    public void copyAsin(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getAsinLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getAsin() != null) consumer.accept(metadata.getAsin());
        }
    }

    public void copyPageCount(boolean clear, Consumer<Integer> consumer) {
        if (!isLocked(metadata.getPageCountLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getPageCount() != null) consumer.accept(metadata.getPageCount());
        }
    }

    public void copyLanguage(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getLanguageLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getLanguage() != null) consumer.accept(metadata.getLanguage());
        }
    }

    public void copyPersonalRating(boolean clear, Consumer<Double> consumer) {
        if (!isLocked(metadata.getPersonalRatingLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getPersonalRating() != null) consumer.accept(metadata.getPersonalRating());
        }
    }

    public void copyGoodreadsId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getGoodreadsIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getGoodreadsId() != null) consumer.accept(metadata.getGoodreadsId());
        }
    }

    public void copyComicvineId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getComicvineIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getComicvineId() != null) consumer.accept(metadata.getComicvineId());
        }
    }

    public void copyHardcoverId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getHardcoverIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getHardcoverId() != null) consumer.accept(metadata.getHardcoverId());
        }
    }

    public void copyGoogleId(boolean clear, Consumer<String> consumer) {
        if (!isLocked(metadata.getGoogleIdLocked())) {
            if (clear) consumer.accept(null);
            else if (metadata.getGoogleId() != null) consumer.accept(metadata.getGoogleId());
        }
    }

    public void copyAuthors(boolean clear, Consumer<Set<String>> consumer) {
        if (!isLocked(metadata.getAuthorsLocked())) {
            if (clear) {
                consumer.accept(Set.of());
            } else if (metadata.getAuthors() != null) {
                Set<String> names = metadata.getAuthors().stream()
                        .map(AuthorEntity::getName)
                        .filter(n -> n != null && !n.isBlank())
                        .collect(Collectors.toSet());
                consumer.accept(names);
            }
        }
    }

    public void copyCategories(boolean clear, Consumer<Set<String>> consumer) {
        if (!isLocked(metadata.getCategoriesLocked())) {
            if (clear) {
                consumer.accept(Set.of());
            } else if (metadata.getCategories() != null) {
                Set<String> cats = metadata.getCategories().stream()
                        .map(CategoryEntity::getName)
                        .filter(n -> n != null && !n.isBlank())
                        .collect(Collectors.toSet());
                consumer.accept(cats);
            }
        }
    }
}