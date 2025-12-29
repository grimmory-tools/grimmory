package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import com.adityachandel.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.WordUtils;
import org.apache.commons.text.similarity.FuzzyScore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class HardcoverParser implements BookParser {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private final HardcoverBookSearchService hardcoverBookSearchService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String isbnCleaned = ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn());
        boolean searchByIsbn = isbnCleaned != null && !isbnCleaned.isBlank();

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
                .map(GraphQLResponse.Hit::getDocument)
                .filter(doc -> {
                    if (searchByIsbn || searchAuthor.isBlank()) return true;

                    if (doc.getAuthorNames() == null || doc.getAuthorNames().isEmpty()) return false;

                    List<String> actualAuthorTokens = doc.getAuthorNames().stream()
                            .map(String::toLowerCase)
                            .flatMap(WHITESPACE_PATTERN::splitAsStream)
                            .toList();
                    List<String> searchAuthorTokens = List.of(WHITESPACE_PATTERN.split(searchAuthor.toLowerCase()));

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
                .map(doc -> {
                    BookMetadata metadata = new BookMetadata();
                    metadata.setHardcoverId(doc.getSlug());
                    // Set numeric book ID for API operations
                    if (doc.getId() != null) {
                        try {
                            metadata.setHardcoverBookId(Integer.parseInt(doc.getId()));
                        } catch (NumberFormatException e) {
                            log.debug("Could not parse Hardcover book ID: {}", doc.getId());
                        }
                    }
                    metadata.setTitle(doc.getTitle());
                    metadata.setSubtitle(doc.getSubtitle());
                    metadata.setDescription(doc.getDescription());
                    if (doc.getAuthorNames() != null) {
                        metadata.setAuthors(Set.copyOf(doc.getAuthorNames()));
                    }

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

                    if (doc.getGenres() != null && !doc.getGenres().isEmpty()) {
                        metadata.setCategories(doc.getGenres().stream()
                                .map(WordUtils::capitalizeFully)
                                .collect(Collectors.toSet()));
                    }
                    if (doc.getMoods() != null && !doc.getMoods().isEmpty()) {
                        metadata.setMoods(doc.getMoods().stream()
                                .map(WordUtils::capitalizeFully)
                                .collect(Collectors.toSet()));
                    }
                    if (doc.getTags() != null && !doc.getTags().isEmpty()) {
                        metadata.setTags(doc.getTags().stream()
                                .map(WordUtils::capitalizeFully)
                                .collect(Collectors.toSet()));
                    }

                    if (doc.getIsbns() != null) {
                        String inputIsbn = fetchMetadataRequest.getIsbn();
                        if (inputIsbn != null && inputIsbn.length() == 10 && doc.getIsbns().contains(inputIsbn)) {
                            metadata.setIsbn10(inputIsbn);
                        } else {
                            metadata.setIsbn10(doc.getIsbns().stream().filter(isbn -> isbn.length() == 10).findFirst().orElse(null));
                        }
                        if (inputIsbn != null && inputIsbn.length() == 13 && doc.getIsbns().contains(inputIsbn)) {
                            metadata.setIsbn13(inputIsbn);
                        } else {
                            metadata.setIsbn13(doc.getIsbns().stream().filter(isbn -> isbn.length() == 13).findFirst().orElse(null));
                        }
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
