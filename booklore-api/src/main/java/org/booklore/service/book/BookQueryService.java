package org.booklore.service.book;

import lombok.RequiredArgsConstructor;
import org.booklore.context.KomgaCleanContext;
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

import java.time.Instant;
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
        return bookRepository.findAllFullBooksBatch(pageable);
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

    public List<BookEntity> getAllFullBookEntities() {
        return bookRepository.findAllFullBooks();
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
                    book.getMetadata().setEmbeddingUpdatedAt(Instant.now());
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
            // Compute allMetadataLocked before potential stripping (though clean mode handles it now)
            m.setAllMetadataLocked(computeAllMetadataLocked(m));
            
            // Enable clean mode for this thread's serialization
            KomgaCleanContext.setCleanMode(true);
            
            // The actual field stripping for JSON is now handled by KomgaCleanBeanPropertyWriter
            // during serialization. We only need to null out fields that might affect 
            // business logic or shouldn't be in the DTO at all for list view.
            
            // Strip external IDs that aren't needed in list view
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

            ComicMetadata cm = m.getComicMetadata();
            if (cm != null) {
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
