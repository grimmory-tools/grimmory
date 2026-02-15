package org.booklore.service.book;

import org.booklore.mapper.v2.BookMapperV2;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.restriction.ContentRestrictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BookQueryService {

    private final BookRepository bookRepository;
    private final BookMapperV2 bookMapperV2;
    private final ContentRestrictionService contentRestrictionService;

    public List<Book> getAllBooks(boolean includeDescription) {
        List<BookEntity> books = bookRepository.findAllWithMetadata();
        return mapBooksToDto(books, includeDescription, null, !includeDescription);
    }

    public List<Book> getAllBooksByLibraryIds(Set<Long> libraryIds, boolean includeDescription, Long userId) {
        List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds);
        books = contentRestrictionService.applyRestrictions(books, userId);
        return mapBooksToDto(books, includeDescription, userId, !includeDescription);
    }

    public List<BookEntity> findAllWithMetadataByIds(Set<Long> bookIds) {
        return bookRepository.findAllWithMetadataByIds(bookIds);
    }

    public List<Book> mapEntitiesToDto(List<BookEntity> entities, boolean includeDescription, Long userId) {
        return mapBooksToDto(entities, includeDescription, userId, !includeDescription);
    }

    public List<BookEntity> getAllFullBookEntities() {
        return bookRepository.findAllFullBooks();
    }

    public void saveAll(List<BookEntity> books) {
        bookRepository.saveAll(books);
    }

    private List<Book> mapBooksToDto(List<BookEntity> books, boolean includeDescription, Long userId, boolean stripForListView) {
        return books.stream()
                .map(book -> mapBookToDto(book, includeDescription, userId, stripForListView))
                .collect(Collectors.toList());
    }

    private Book mapBookToDto(BookEntity bookEntity, boolean includeDescription, Long userId, boolean stripForListView) {
        Book dto = bookMapperV2.toDTO(bookEntity);

        if (!includeDescription && dto.getMetadata() != null) {
            dto.getMetadata().setDescription(null);
        }

        if (dto.getShelves() != null && userId != null) {
            dto.setShelves(dto.getShelves().stream()
                    .filter(shelf -> userId.equals(shelf.getUserId()))
                    .collect(Collectors.toSet()));
        }

        if (stripForListView) {
            stripFieldsForListView(dto);
        }

        return dto;
    }

    private void stripFieldsForListView(Book dto) {
        dto.setLibraryPath(null);

        BookMetadata m = dto.getMetadata();
        if (m != null) {
            // Compute allMetadataLocked before stripping lock flags
            m.setAllMetadataLocked(computeAllMetadataLocked(m));

            // Strip lock flags
            m.setTitleLocked(null);
            m.setSubtitleLocked(null);
            m.setPublisherLocked(null);
            m.setPublishedDateLocked(null);
            m.setDescriptionLocked(null);
            m.setSeriesNameLocked(null);
            m.setSeriesNumberLocked(null);
            m.setSeriesTotalLocked(null);
            m.setIsbn13Locked(null);
            m.setIsbn10Locked(null);
            m.setAsinLocked(null);
            m.setGoodreadsIdLocked(null);
            m.setComicvineIdLocked(null);
            m.setHardcoverIdLocked(null);
            m.setHardcoverBookIdLocked(null);
            m.setDoubanIdLocked(null);
            m.setGoogleIdLocked(null);
            m.setPageCountLocked(null);
            m.setLanguageLocked(null);
            m.setAmazonRatingLocked(null);
            m.setAmazonReviewCountLocked(null);
            m.setGoodreadsRatingLocked(null);
            m.setGoodreadsReviewCountLocked(null);
            m.setHardcoverRatingLocked(null);
            m.setHardcoverReviewCountLocked(null);
            m.setDoubanRatingLocked(null);
            m.setDoubanReviewCountLocked(null);
            m.setLubimyczytacIdLocked(null);
            m.setLubimyczytacRatingLocked(null);
            m.setRanobedbIdLocked(null);
            m.setRanobedbRatingLocked(null);
            m.setAudibleIdLocked(null);
            m.setAudibleRatingLocked(null);
            m.setAudibleReviewCountLocked(null);
            m.setExternalUrlLocked(null);
            m.setCoverLocked(null);
            m.setAudiobookCoverLocked(null);
            m.setAuthorsLocked(null);
            m.setCategoriesLocked(null);
            m.setMoodsLocked(null);
            m.setTagsLocked(null);
            m.setReviewsLocked(null);
            m.setNarratorLocked(null);
            m.setAbridgedLocked(null);
            m.setAgeRatingLocked(null);
            m.setContentRatingLocked(null);

            // Strip external IDs
            m.setAsin(null);
            m.setGoodreadsId(null);
            m.setComicvineId(null);
            m.setHardcoverId(null);
            m.setHardcoverBookId(null);
            m.setGoogleId(null);
            m.setLubimyczytacId(null);
            m.setRanobedbId(null);
            m.setAudibleId(null);
            m.setDoubanId(null);

            // Strip unused detail fields
            m.setSubtitle(null);
            m.setSeriesTotal(null);
            m.setAbridged(null);
            m.setExternalUrl(null);
            m.setThumbnailUrl(null);
            m.setProvider(null);
            m.setAudiobookMetadata(null);
            m.setBookReviews(null);

            // Strip unused ratings
            m.setDoubanRating(null);
            m.setDoubanReviewCount(null);
            m.setAudibleRating(null);
            m.setAudibleReviewCount(null);
            m.setLubimyczytacRating(null);

            // Strip empty metadata collections
            if (m.getMoods() != null && m.getMoods().isEmpty()) m.setMoods(null);
            if (m.getTags() != null && m.getTags().isEmpty()) m.setTags(null);
            if (m.getAuthors() != null && m.getAuthors().isEmpty()) m.setAuthors(null);
            if (m.getCategories() != null && m.getCategories().isEmpty()) m.setCategories(null);

            // Strip ComicMetadata fields
            ComicMetadata cm = m.getComicMetadata();
            if (cm != null) {
                // Strip comic lock flags
                cm.setIssueNumberLocked(null);
                cm.setVolumeNameLocked(null);
                cm.setVolumeNumberLocked(null);
                cm.setStoryArcLocked(null);
                cm.setStoryArcNumberLocked(null);
                cm.setAlternateSeriesLocked(null);
                cm.setAlternateIssueLocked(null);
                cm.setImprintLocked(null);
                cm.setFormatLocked(null);
                cm.setBlackAndWhiteLocked(null);
                cm.setMangaLocked(null);
                cm.setReadingDirectionLocked(null);
                cm.setWebLinkLocked(null);
                cm.setNotesLocked(null);
                cm.setCreatorsLocked(null);
                cm.setPencillersLocked(null);
                cm.setInkersLocked(null);
                cm.setColoristsLocked(null);
                cm.setLetterersLocked(null);
                cm.setCoverArtistsLocked(null);
                cm.setEditorsLocked(null);
                cm.setCharactersLocked(null);
                cm.setTeamsLocked(null);
                cm.setLocationsLocked(null);

                // Strip non-filter detail fields
                cm.setIssueNumber(null);
                cm.setVolumeName(null);
                cm.setVolumeNumber(null);
                cm.setStoryArc(null);
                cm.setStoryArcNumber(null);
                cm.setAlternateSeries(null);
                cm.setAlternateIssue(null);
                cm.setImprint(null);
                cm.setFormat(null);
                cm.setBlackAndWhite(null);
                cm.setManga(null);
                cm.setReadingDirection(null);
                cm.setWebLink(null);
                cm.setNotes(null);
            }
        }

        // Strip empty book-level collections
        if (dto.getAlternativeFormats() != null && dto.getAlternativeFormats().isEmpty()) dto.setAlternativeFormats(null);
        if (dto.getSupplementaryFiles() != null && dto.getSupplementaryFiles().isEmpty()) dto.setSupplementaryFiles(null);
    }

    private boolean computeAllMetadataLocked(BookMetadata m) {
        Boolean[] bookLocks = {
                m.getTitleLocked(), m.getSubtitleLocked(), m.getPublisherLocked(),
                m.getPublishedDateLocked(), m.getDescriptionLocked(), m.getSeriesNameLocked(),
                m.getSeriesNumberLocked(), m.getSeriesTotalLocked(), m.getIsbn13Locked(),
                m.getIsbn10Locked(), m.getAsinLocked(), m.getGoodreadsIdLocked(),
                m.getComicvineIdLocked(), m.getHardcoverIdLocked(), m.getHardcoverBookIdLocked(),
                m.getDoubanIdLocked(), m.getGoogleIdLocked(), m.getPageCountLocked(),
                m.getLanguageLocked(), m.getAmazonRatingLocked(), m.getAmazonReviewCountLocked(),
                m.getGoodreadsRatingLocked(), m.getGoodreadsReviewCountLocked(),
                m.getHardcoverRatingLocked(), m.getHardcoverReviewCountLocked(),
                m.getDoubanRatingLocked(), m.getDoubanReviewCountLocked(),
                m.getLubimyczytacIdLocked(), m.getLubimyczytacRatingLocked(),
                m.getRanobedbIdLocked(), m.getRanobedbRatingLocked(),
                m.getAudibleIdLocked(), m.getAudibleRatingLocked(), m.getAudibleReviewCountLocked(),
                m.getExternalUrlLocked(), m.getCoverLocked(), m.getAudiobookCoverLocked(),
                m.getAuthorsLocked(), m.getCategoriesLocked(), m.getMoodsLocked(),
                m.getTagsLocked(), m.getReviewsLocked(), m.getNarratorLocked(),
                m.getAbridgedLocked(), m.getAgeRatingLocked(), m.getContentRatingLocked()
        };

        boolean hasAnyLock = false;
        for (Boolean lock : bookLocks) {
            if (Boolean.TRUE.equals(lock)) {
                hasAnyLock = true;
            } else {
                return false;
            }
        }
        return hasAnyLock;
    }
}
