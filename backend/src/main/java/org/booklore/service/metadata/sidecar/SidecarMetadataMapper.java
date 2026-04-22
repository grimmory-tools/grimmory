package org.booklore.service.metadata.sidecar;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.sidecar.*;
import org.booklore.model.entity.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SidecarMetadataMapper {

    public SidecarMetadata toSidecarMetadata(BookMetadataEntity entity, String coverFileName) {
        if (entity == null) {
            return null;
        }

        SidecarBookMetadata bookMetadata = SidecarBookMetadata.builder()
                .title(entity.getTitle())
                .subtitle(entity.getSubtitle())
                .authors(extractNames(entity.getAuthors()))
                .publisher(entity.getPublisher())
                .publishedDate(entity.getPublishedDate())
                .description(entity.getDescription())
                .isbn10(entity.getIsbn10())
                .isbn13(entity.getIsbn13())
                .language(entity.getLanguage())
                .pageCount(entity.getPageCount())
                .categories(extractCategoryNames(entity.getCategories()))
                .moods(extractMoodNames(entity.getMoods()))
                .tags(extractTagNames(entity.getTags()))
                .series(buildSeries(entity))
                .identifiers(buildIdentifiers(entity))
                .ratings(buildRatings(entity))
                .ageRating(entity.getAgeRating())
                .contentRating(entity.getContentRating())
                .narrator(entity.getNarrator())
                .abridged(entity.getAbridged())
                .comicMetadata(buildComicMetadata(entity.getComicMetadata()))
                .build();

        SidecarCoverInfo coverInfo = null;
        if (StringUtils.hasText(coverFileName)) {
            coverInfo = SidecarCoverInfo.builder()
                    .source("external")
                    .path(coverFileName)
                    .build();
        }

        return SidecarMetadata.builder()
                .version("1.0")
                .generatedAt(Instant.now())
                .generatedBy("booklore")
                .metadata(bookMetadata)
                .cover(coverInfo)
                .build();
    }

    public BookMetadata toBookMetadata(SidecarMetadata sidecar) {
        if (sidecar == null || sidecar.getMetadata() == null) {
            return null;
        }

        SidecarBookMetadata m = sidecar.getMetadata();

        BookMetadata.BookMetadataBuilder builder = BookMetadata.builder()
                .title(m.getTitle())
                .subtitle(m.getSubtitle())
                .authors(m.getAuthors())
                .publisher(m.getPublisher())
                .publishedDate(m.getPublishedDate())
                .description(m.getDescription())
                .isbn10(m.getIsbn10())
                .isbn13(m.getIsbn13())
                .language(m.getLanguage())
                .pageCount(m.getPageCount())
                .categories(m.getCategories())
                .moods(m.getMoods())
                .tags(m.getTags())
                .ageRating(m.getAgeRating())
                .contentRating(m.getContentRating())
                .narrator(m.getNarrator())
                .abridged(m.getAbridged())
                .comicMetadata(m.getComicMetadata());

        if (m.getSeries() != null) {
            builder.seriesName(m.getSeries().getName())
                    .seriesNumber(m.getSeries().getNumber())
                    .seriesTotal(m.getSeries().getTotal());
        }

        if (m.getIdentifiers() != null) {
            SidecarIdentifiers ids = m.getIdentifiers();
            builder.asin(ids.getAsin())
                    .goodreadsId(ids.getGoodreadsId())
                    .googleId(ids.getGoogleId())
                    .hardcoverId(ids.getHardcoverId())
                    .hardcoverBookId(ids.getHardcoverBookId())
                    .comicvineId(ids.getComicvineId())
                    .lubimyczytacId(ids.getLubimyczytacId())
                    .ranobedbId(ids.getRanobedbId())
                    .audibleId(ids.getAudibleId());
        }

        if (m.getRatings() != null) {
            SidecarRatings r = m.getRatings();
            if (r.getAmazon() != null) {
                builder.amazonRating(r.getAmazon().getAverage())
                        .amazonReviewCount(r.getAmazon().getCount());
            }
            if (r.getGoodreads() != null) {
                builder.goodreadsRating(r.getGoodreads().getAverage())
                        .goodreadsReviewCount(r.getGoodreads().getCount());
            }
            if (r.getHardcover() != null) {
                builder.hardcoverRating(r.getHardcover().getAverage())
                        .hardcoverReviewCount(r.getHardcover().getCount());
            }
            if (r.getLubimyczytac() != null) {
                builder.lubimyczytacRating(r.getLubimyczytac().getAverage());
            }
            if (r.getRanobedb() != null) {
                builder.ranobedbRating(r.getRanobedb().getAverage());
            }
            if (r.getAudible() != null) {
                builder.audibleRating(r.getAudible().getAverage())
                        .audibleReviewCount(r.getAudible().getCount());
            }
        }

        return builder.build();
    }

    public String getCoverFileName(Path bookPath) {
        String fileName = bookPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        return baseName + ".cover.jpg";
    }

    private List<String> extractNames(List<AuthorEntity> entities) {
        if (entities == null) return null;
        return entities.stream()
                .map(AuthorEntity::getName)
                .toList();
    }

    private Set<String> extractCategoryNames(Set<CategoryEntity> entities) {
        if (entities == null) return null;
        return entities.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.toSet());
    }

    private Set<String> extractMoodNames(Set<MoodEntity> entities) {
        if (entities == null) return null;
        return entities.stream()
                .map(MoodEntity::getName)
                .collect(Collectors.toSet());
    }

    private Set<String> extractTagNames(Set<TagEntity> entities) {
        if (entities == null) return null;
        return entities.stream()
                .map(TagEntity::getName)
                .collect(Collectors.toSet());
    }

    private SidecarSeries buildSeries(BookMetadataEntity entity) {
        if (entity.getSeriesName() == null && entity.getSeriesNumber() == null && entity.getSeriesTotal() == null) {
            return null;
        }
        return SidecarSeries.builder()
                .name(entity.getSeriesName())
                .number(entity.getSeriesNumber())
                .total(entity.getSeriesTotal())
                .build();
    }

    private SidecarIdentifiers buildIdentifiers(BookMetadataEntity entity) {
        return SidecarIdentifiers.builder()
                .asin(entity.getAsin())
                .goodreadsId(entity.getGoodreadsId())
                .googleId(entity.getGoogleId())
                .hardcoverId(entity.getHardcoverId())
                .hardcoverBookId(entity.getHardcoverBookId())
                .comicvineId(entity.getComicvineId())
                .lubimyczytacId(entity.getLubimyczytacId())
                .ranobedbId(entity.getRanobedbId())
                .audibleId(entity.getAudibleId())
                .build();
    }

    private SidecarRatings buildRatings(BookMetadataEntity entity) {
        return SidecarRatings.builder()
                .amazon(buildRating(entity.getAmazonRating(), entity.getAmazonReviewCount()))
                .goodreads(buildRating(entity.getGoodreadsRating(), entity.getGoodreadsReviewCount()))
                .hardcover(buildRating(entity.getHardcoverRating(), entity.getHardcoverReviewCount()))
                .lubimyczytac(buildRating(entity.getLubimyczytacRating(), null))
                .ranobedb(buildRating(entity.getRanobedbRating(), null))
                .audible(buildRating(entity.getAudibleRating(), entity.getAudibleReviewCount()))
                .build();
    }

    private SidecarRating buildRating(Double average, Integer count) {
        if (average == null && count == null) {
            return null;
        }
        return SidecarRating.builder()
                .average(average)
                .count(count)
                .build();
    }

    private ComicMetadata buildComicMetadata(ComicMetadataEntity entity) {
        if (entity == null) {
            return null;
        }
        return ComicMetadata.builder()
                .issueNumber(entity.getIssueNumber())
                .volumeName(entity.getVolumeName())
                .volumeNumber(entity.getVolumeNumber())
                .storyArc(entity.getStoryArc())
                .storyArcNumber(entity.getStoryArcNumber())
                .alternateSeries(entity.getAlternateSeries())
                .alternateIssue(entity.getAlternateIssue())
                .imprint(entity.getImprint())
                .format(entity.getFormat())
                .blackAndWhite(entity.getBlackAndWhite())
                .manga(entity.getManga())
                .readingDirection(entity.getReadingDirection())
                .webLink(entity.getWebLink())
                .notes(entity.getNotes())
                .characters(entity.getCharacters() != null ?
                        entity.getCharacters().stream().map(ComicCharacterEntity::getName).collect(Collectors.toSet()) : null)
                .teams(entity.getTeams() != null ?
                        entity.getTeams().stream().map(ComicTeamEntity::getName).collect(Collectors.toSet()) : null)
                .locations(entity.getLocations() != null ?
                        entity.getLocations().stream().map(ComicLocationEntity::getName).collect(Collectors.toSet()) : null)
                .build();
    }
}
