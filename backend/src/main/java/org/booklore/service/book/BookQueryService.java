package org.booklore.service.book;

import lombok.RequiredArgsConstructor;
import org.booklore.mapper.v2.BookMapperV2;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookRecommendationLite;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.restriction.ContentRestrictionService;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class BookQueryService {

    private final BookRepository bookRepository;
    private final BookMapperV2 bookMapperV2;
    private final ContentRestrictionService contentRestrictionService;

    public List<Book> getAllBooks(boolean includeDescription, boolean stripForListView) {
        List<BookEntity> books = bookRepository.findAllWithMetadata();
        return mapBooksToDto(books, includeDescription, null, stripForListView);
    }

    public List<Book> getAllBooksByLibraryIds(Set<Long> libraryIds, boolean includeDescription, boolean StripForListView, Long userId) {
        List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds);
        books = contentRestrictionService.applyRestrictions(books, userId);
        return mapBooksToDto(books, includeDescription, userId, StripForListView);
    }

    public Page<Book> getAllBooksPaged(Pageable pageable) {
        Page<BookEntity> page = bookRepository.findAllWithMetadataPage(pageable);
        return page.map(book -> mapBookToDto(book, false, null, true));
    }

    public Page<Book> getAllBooksByLibraryIdsPaged(Collection<Long> libraryIds, Long userId, Pageable pageable) {
        Page<BookEntity> page = bookRepository.findAllWithMetadataByLibraryIdsPage(libraryIds, pageable);
        List<BookEntity> filtered = contentRestrictionService.applyRestrictions(page.getContent(), userId);
        List<Book> dtos = filtered.stream()
                .map(book -> mapBookToDto(book, false, userId, true))
                .toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public List<BookEntity> getAllFullBookEntitiesBatch(Pageable pageable) {
        List<BookEntity> books = bookRepository.findAllFullBooksBatch(pageable);
        for (BookEntity book : books) {
            if (book.getMetadata() != null) {
                Hibernate.initialize(book.getMetadata().getAuthors());
                Hibernate.initialize(book.getMetadata().getCategories());
            }
        }
        return books;
    }

    public long countAllNonDeleted() {
        return bookRepository.countNonDeleted();
    }

    public List<BookEntity> findAllWithMetadataByIds(Set<Long> bookIds) {
        return bookRepository.findAllWithMetadataByIds(bookIds);
    }

    public List<Book> mapEntitiesToDto(List<BookEntity> entities, boolean includeDescription, Long userId) {
        return mapBooksToDto(entities, includeDescription, userId, !includeDescription);
    }

    @Transactional(readOnly = true)
    public List<BookRegenerationInfo> findAllRegenerationInfo(boolean missingOnly) {
        return bookRepository.findAllFullBooksWithFiles().stream()
                .filter(book -> book.getMetadata() != null)
                .filter(book -> !isCoverLocked(book))
                .filter(book -> book.getPrimaryBookFile() != null)
                .filter(book -> !missingOnly || book.getBookCoverHash() == null)
                .map(book -> new BookRegenerationInfo(book.getId(), book.getMetadata().getTitle()))
                .toList();
    }

    private boolean isCoverLocked(BookEntity book) {
        return book.getMetadata() != null && Boolean.TRUE.equals(book.getMetadata().getCoverLocked());
    }

    public record BookRegenerationInfo(Long id, String title) {}

    public List<BookEntity> getAllFullBookEntities() {
        return bookRepository.findAllFullBooks();
    }

    public List<BookEntity> getAllFullBookEntitiesWithFiles() {
        return bookRepository.findAllFullBooksWithFiles();
    }

    @Transactional
    public void saveAll(List<BookEntity> books) {
        bookRepository.saveAll(books);
    }

    @Transactional
    public void compareAndSaveEmbeddings(Map<Long, String> embeddingJsonByBookId) {
        List<BookEntity> books = bookRepository.findAllWithMetadataByIds(new HashSet<>(embeddingJsonByBookId.keySet()));
        for (BookEntity book : books) {
            String embeddingJson = embeddingJsonByBookId.get(book.getId());
            if (embeddingJson != null && book.getMetadata() != null) {
                if (!Objects.equals(book.getMetadata().getEmbeddingVector(), embeddingJson)) {
                    book.getMetadata().setEmbeddingVector(embeddingJson);
                    book.getMetadata().setEmbeddingUpdatedAt(java.time.Instant.now());
                }
            }
        }
    }

    @Transactional
    public void saveRecommendationsInBatches(Map<Long, Set<BookRecommendationLite>> recommendations, int batchSize) {
        List<Long> bookIds = new ArrayList<>(recommendations.keySet());
        for (int i = 0; i < bookIds.size(); i += batchSize) {
            List<Long> batchIds = bookIds.subList(i, Math.min(i + batchSize, bookIds.size()));
            List<BookEntity> batch = bookRepository.findAllById(batchIds);
            for (BookEntity book : batch) {
                Set<BookRecommendationLite> recs = recommendations.get(book.getId());
                if (recs != null) {
                    book.setSimilarBooksJson(recs);
                }
            }
            bookRepository.saveAll(batch);
        }
    }

    private List<Book> mapBooksToDto(List<BookEntity> books, boolean includeDescription, Long userId, boolean stripForListView) {
        return books.stream()
                .map(book -> mapBookToDto(book, includeDescription, userId, stripForListView))
                .collect(Collectors.toList());
    }

    private Book mapBookToDto(BookEntity bookEntity, boolean includeDescription, Long userId, boolean stripForListView) {
        Book dto = bookMapperV2.toDTO(bookEntity);

        if (includeDescription && dto.getMetadata() != null && bookEntity.getMetadata() != null) {
            dto.getMetadata().setDescription(bookEntity.getMetadata().getDescription());
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
            if (m.getAudiobookMetadata() != null) {
                m.getAudiobookMetadata().setChapters(null);
            }
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
