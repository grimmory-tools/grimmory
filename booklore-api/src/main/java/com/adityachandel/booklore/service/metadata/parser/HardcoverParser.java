package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import com.adityachandel.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.commons.text.similarity.FuzzyScore;

import java.util.Locale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class HardcoverParser implements BookParser {

    private final HardcoverBookSearchService hardcoverBookSearchService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {

        boolean searchByIsbn = fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isBlank();

        List<GraphQLResponse.Hit> hits;
        if (searchByIsbn) {
            log.info("Hardcover: Fetching metadata using ISBN {}", fetchMetadataRequest.getIsbn());
            hits = hardcoverBookSearchService.searchBooks(fetchMetadataRequest.getIsbn());
        } else {
            log.info("Hardcover: Fetching metadata using title '{}'", fetchMetadataRequest.getTitle());
            hits = hardcoverBookSearchService.searchBooks(fetchMetadataRequest.getTitle());
        }

        if (hits == null || hits.isEmpty()) {
            log.info("Hardcover: No results found for {}", searchByIsbn ? "ISBN " + fetchMetadataRequest.getIsbn() : "title " + fetchMetadataRequest.getTitle());
            return List.of();
        }

        FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);
        String searchAuthor = fetchMetadataRequest.getAuthor() != null ? fetchMetadataRequest.getAuthor() : "";

        return hits.stream()
                .filter(hit -> {
                    if (searchByIsbn || searchAuthor.isBlank()) return true;

                    List<String> actualAuthorTokens = hit.getDocument().getAuthorNames().stream()
                            .flatMap(name -> List.of(name.toLowerCase().split("\\s+")).stream())
                            .toList();
                    List<String> searchAuthorTokens = List.of(searchAuthor.toLowerCase().split("\\s+"));

                    for (String actual : actualAuthorTokens) {
                        for (String query : searchAuthorTokens) {
                            int score = fuzzyScore.fuzzyScore(actual, query);
                            int maxScore = Math.max(fuzzyScore.fuzzyScore(query, query), fuzzyScore.fuzzyScore(actual, actual));
                            double similarity = maxScore > 0 ? (double) score / maxScore : 0;
                            if (similarity >= 0.5) return true;
                        }
                    }
                    return false;
                })
                .map(hit -> {
                    GraphQLResponse.Document doc = hit.getDocument();
                    BookMetadata metadata = new BookMetadata();
                    metadata.setHardcoverId(doc.getSlug());
                    metadata.setTitle(doc.getTitle());
                    metadata.setDescription(doc.getDescription());
                    metadata.setAuthors(doc.getAuthorNames());

                    if (doc.getFeaturedSeries() != null) {
                        if (doc.getFeaturedSeries().getSeries() != null) {
                            metadata.setSeriesName(doc.getFeaturedSeries().getSeries().getName());
                            metadata.setSeriesTotal(doc.getFeaturedSeries().getSeries().getBooksCount());
                        }
                        if (doc.getFeaturedSeries().getPosition() != null) {
                            try {
                                metadata.setSeriesNumber(Float.parseFloat(String.valueOf(doc.getFeaturedSeries().getPosition())));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    if (doc.getRating() != null) {
                        metadata.setHardcoverRating(BigDecimal.valueOf(doc.getRating()).setScale(2, RoundingMode.HALF_UP).doubleValue());
                    }
                    metadata.setHardcoverReviewCount(doc.getRatingsCount());
                    metadata.setPageCount(doc.getPages());
                    metadata.setPublishedDate(doc.getReleaseDate() != null ? LocalDate.parse(doc.getReleaseDate()) : null);
                    metadata.setCategories(doc.getGenres());

                    if (doc.getIsbns() != null) {
                        metadata.setIsbn10(doc.getIsbns().stream().filter(isbn -> isbn.length() == 10).findFirst().orElse(null));
                        metadata.setIsbn13(doc.getIsbns().stream().filter(isbn -> isbn.length() == 13).findFirst().orElse(null));
                    }

                    metadata.setThumbnailUrl(doc.getImage() != null ? doc.getImage().getUrl() : null);
                    metadata.setProvider(MetadataProvider.Hardcover);
                    return metadata;
                })
                .toList();
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> bookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return bookMetadata.isEmpty() ? null : bookMetadata.getFirst();
    }
}
