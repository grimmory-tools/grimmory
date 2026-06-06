package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import org.booklore.service.metadata.parser.hardcover.HardcoverBookDetails;
import org.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import org.booklore.service.metadata.parser.hardcover.HardcoverMoodFilter;
import org.booklore.util.BookUtils;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.commons.text.similarity.FuzzyScore;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class HardcoverParser implements BookParser {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final double AUTHOR_MATCH_THRESHOLD = 0.5;

    private final HardcoverBookSearchService hardcoverBookSearchService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String isbnCleaned = ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn());
        boolean searchByIsbn = isbnCleaned != null && !isbnCleaned.isBlank();

        if (searchByIsbn) {
            log.info("Hardcover: Fetching metadata using ISBN {}", isbnCleaned);
            List<GraphQLResponse.BookWithEditions> hits = hardcoverBookSearchService.searchBookByIsbn(isbnCleaned);
            return processBooksWithEditions(hits);
        }

        String title = fetchMetadataRequest.getTitle();
        String author = fetchMetadataRequest.getAuthor();

        if (title == null || title.isBlank()) {
            log.warn("Hardcover: No title provided for search");
            return Collections.emptyList();
        }

        List<BookMetadata> results = Collections.emptyList();

        // 1. Try Title + Author
        if (author != null && !author.isBlank()) {
            String combinedQuery = title.trim() + " " + author.trim();
            log.info("Hardcover: Searching with title+author: '{}'", combinedQuery);
            List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(combinedQuery);
            results = processHits(hits, fetchMetadataRequest, false);
        }

        // 2. If no valid results found (or no author provided), Try Title only
        if (results.isEmpty()) {
            log.info("Hardcover: Searching with title only: '{}'", title);
            List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(title.trim());
            results = processHits(hits, fetchMetadataRequest, false);
        }

        if (results.isEmpty()) {
            log.info("Hardcover: No results found for title '{}'", title);
        }

        return results;
    }

    private List<BookMetadata> processBooksWithEditions(List<GraphQLResponse.BookWithEditions> books) {
        if (books == null || books.isEmpty()) {
            return Collections.emptyList();
        }

        List<BookMetadata> results = new ArrayList<>();
        for (GraphQLResponse.BookWithEditions book : books) {
            if (book.getEditions() == null || book.getEditions().isEmpty()) {
                continue;
            }
            for (GraphQLResponse.Edition edition : book.getEditions()) {
                log.debug("Processing edition '{}' with id '{}' of book '{}'", edition.getTitle(), edition.getId(), book.getTitle());
                BookMetadata metadata = mapEditionToMetadata(edition, book);
                if (metadata != null) {
                    results.add(metadata);
                }
            }
        }
        return results;
    }

    private List<BookMetadata> processHits(List<GraphQLResponse.Hit> hits, FetchMetadataRequest request, boolean searchByIsbn) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }

        FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);
        String searchAuthor = request.getAuthor() != null ? request.getAuthor() : "";

        // Filter by author
        List<GraphQLResponse.Document> matchedDocs = hits.stream()
                .map(GraphQLResponse.Hit::getDocument)
                .filter(doc -> filterByAuthor(doc, searchAuthor, searchByIsbn, fuzzyScore))
                .toList();

        if (matchedDocs.isEmpty()) {
            return Collections.emptyList();
        }

        // Only fetch detailed mood data for the TOP match to minimize API calls
        List<BookMetadata> results = new ArrayList<>();
        boolean isFirst = true;

        for (GraphQLResponse.Document doc : matchedDocs) {
            BookMetadata metadata = mapDocumentToMetadata(doc, request, isFirst);
            results.add(metadata);
            isFirst = false;
        }

        return results;
    }

    private boolean filterByAuthor(GraphQLResponse.Document doc, String searchAuthor,
                                   boolean searchByIsbn, FuzzyScore fuzzyScore) {
        // Skip author filtering for ISBN searches or when no author provided
        if (searchByIsbn || searchAuthor.isBlank()) {
            return true;
        }

        if (doc.getAuthorNames() == null || doc.getAuthorNames().isEmpty()) {
            return false;
        }

        List<String> actualAuthorTokens = doc.getAuthorNames().stream()
                .map(String::toLowerCase)
                .flatMap(WHITESPACE_PATTERN::splitAsStream)
                .toList();
        List<String> searchAuthorTokens = List.of(WHITESPACE_PATTERN.split(searchAuthor.toLowerCase()));

        for (String actual : actualAuthorTokens) {
            for (String query : searchAuthorTokens) {
                int score = fuzzyScore.fuzzyScore(actual, query);
                int maxScore = Math.max(
                        fuzzyScore.fuzzyScore(query, query),
                        fuzzyScore.fuzzyScore(actual, actual)
                );
                double similarity = maxScore > 0 ? (double) score / maxScore : 0;
                if (similarity >= AUTHOR_MATCH_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    private BookMetadata mapDocumentToMetadata(GraphQLResponse.Document doc, FetchMetadataRequest request, boolean fetchDetailedMoods) {
        BookMetadata metadata = new BookMetadata();
        metadata.setHardcoverId(doc.getSlug());

        String bookId = parseBookId(doc.getId());
        if (bookId != null) {
            metadata.setHardcoverBookId(bookId);
        }

        metadata.setTitle(doc.getTitle());
        metadata.setSubtitle(doc.getSubtitle());
        metadata.setDescription(doc.getDescription());

        if (doc.getAuthorNames() != null) {
            metadata.setAuthors(List.copyOf(doc.getAuthorNames()));
        }

        mapSeriesInfo(doc, metadata);

        if (doc.getRating() != null) {
            metadata.setHardcoverRating(
                    BigDecimal.valueOf(doc.getRating()).setScale(2, RoundingMode.HALF_UP).doubleValue()
            );
        }
        metadata.setHardcoverReviewCount(doc.getRatingsCount());
        metadata.setPageCount(doc.getPages());

        if (doc.getReleaseDate() != null) {
            try {
                metadata.setPublishedDate(LocalDate.parse(doc.getReleaseDate()));
            } catch (Exception e) {
                log.debug("Could not parse release date: {}", doc.getReleaseDate());
            }
        }

        mapTagsAndMoods(doc, metadata, bookId, fetchDetailedMoods);
        mapIsbns(doc, request, metadata);

        metadata.setThumbnailUrl(doc.getImage() != null ? doc.getImage().getUrl() : null);
        metadata.setProvider(MetadataProvider.Hardcover);
        return metadata;
    }

    private BookMetadata mapEditionToMetadata(GraphQLResponse.Edition edition, GraphQLResponse.BookWithEditions book) {
        BookMetadata metadata = new BookMetadata();
        metadata.setHardcoverId(book.getSlug());

        Integer bookId = book.getId();
        if (bookId != null) {
            metadata.setHardcoverBookId(bookId.toString());
        }

        metadata.setTitle(edition.getTitle());
        metadata.setSubtitle(edition.getSubtitle());
        metadata.setDescription(book.getDescription());

        if (edition.getCachedContributors() != null) {
            metadata.setAuthors(edition.getCachedContributors().stream()
                    .map(GraphQLResponse.Contributor::getAuthor)
                    .filter(Objects::nonNull)
                    .map(GraphQLResponse.Author::getName)
                    .filter(Objects::nonNull)
                    .toList());
        }

        if (book.getFeaturedBookSeries() != null && book.getFeaturedBookSeries().getSeries() != null) {
            metadata.setSeriesName(book.getFeaturedBookSeries().getSeries().getName());
            metadata.setSeriesTotal(book.getFeaturedBookSeries().getSeries().getPrimaryBooksCount());

            if (book.getFeaturedBookSeries().getPosition() != null) {
                try {
                    metadata.setSeriesNumber(Float.parseFloat(String.valueOf(book.getFeaturedBookSeries().getPosition())));
                } catch (NumberFormatException _) {
                }
            }
        }

        if (book.getRating() != null) {
            metadata.setHardcoverRating(
                    BigDecimal.valueOf(book.getRating()).setScale(2, RoundingMode.HALF_UP).doubleValue()
            );
        }
        metadata.setHardcoverReviewCount(book.getRatingsCount());
        metadata.setPageCount(edition.getPages());

        if (edition.getReleaseDate() != null) {
            try {
                metadata.setPublishedDate(LocalDate.parse(edition.getReleaseDate()));
            } catch (Exception e) {
                log.debug("Could not parse release date: {}", edition.getReleaseDate());
            }
        }

        // Set the language from the edition
        if (edition.getLanguage() != null && edition.getLanguage().getCode2() != null) {
            metadata.setLanguage(edition.getLanguage().getCode2());
        }

        // Set the Publisher from the edition
        if (edition.getPublisher() != null && edition.getPublisher().getName() != null) {
            metadata.setPublisher(edition.getPublisher().getName());
        }

        GraphQLResponse.CachedTags cachedTags = book.getCachedTags();

        if (cachedTags != null && cachedTags.getMood() != null && !cachedTags.getMood().isEmpty()) {
            Set<String> basicFilteredMoods = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags.getMood());
            metadata.setMoods(basicFilteredMoods.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        if (cachedTags != null && cachedTags.getGenre() != null && !cachedTags.getGenre().isEmpty()) {
            Set<String> filteredGenres = HardcoverMoodFilter.filterGenresWithCounts(cachedTags.getGenre());
            metadata.setCategories(filteredGenres.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }

        if (cachedTags != null && cachedTags.getTag() != null && !cachedTags.getTag().isEmpty()) {
            Set<String> filteredTags = HardcoverMoodFilter.filterTagsWithCounts(cachedTags.getTag());
            metadata.setTags(filteredTags.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }

        metadata.setIsbn10(edition.getIsbn10());
        metadata.setIsbn13(edition.getIsbn13());

        // If only one ISBN is provided, calculate the other
        if (metadata.getIsbn10() != null && metadata.getIsbn13() == null) {
            metadata.setIsbn13(BookUtils.isbn10To13(edition.getIsbn10()));
        } else if (metadata.getIsbn13() != null && metadata.getIsbn10() == null) {
            metadata.setIsbn10(BookUtils.isbn13to10(edition.getIsbn13()));
        }

        metadata.setThumbnailUrl(edition.getImage() != null ? edition.getImage().getUrl() : null);
        metadata.setProvider(MetadataProvider.Hardcover);

        return metadata;
    }

    private String parseBookId(String id) {
        return id;
    }

    private void mapSeriesInfo(GraphQLResponse.Document doc, BookMetadata metadata) {
        if (doc.getFeaturedSeries() == null) {
            return;
        }
        if (doc.getFeaturedSeries().getSeries() != null) {
            metadata.setSeriesName(doc.getFeaturedSeries().getSeries().getName());
            metadata.setSeriesTotal(doc.getFeaturedSeries().getSeries().getPrimaryBooksCount());
        }
        if (doc.getFeaturedSeries().getPosition() != null) {
            try {
                metadata.setSeriesNumber(Float.parseFloat(String.valueOf(doc.getFeaturedSeries().getPosition())));
            } catch (NumberFormatException _) {
            }
        }
    }

    private void mapTagsAndMoods(GraphQLResponse.Document doc, BookMetadata metadata, String bookId, boolean fetchDetailedMoods) {
        boolean usedDetailedMoods = false;

        if (fetchDetailedMoods && bookId != null) {
            usedDetailedMoods = tryFetchDetailedMoods(bookId, metadata);
        }

        if (!usedDetailedMoods && doc.getMoods() != null && !doc.getMoods().isEmpty()) {
            Set<String> basicFilteredMoods = HardcoverMoodFilter.filterBasicMoods(doc.getMoods());
            metadata.setMoods(basicFilteredMoods.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        if ((metadata.getCategories() == null || metadata.getCategories().isEmpty())
                && doc.getGenres() != null && !doc.getGenres().isEmpty()) {
            metadata.setCategories(doc.getGenres().stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }

        if ((metadata.getTags() == null || metadata.getTags().isEmpty())
                && doc.getTags() != null && !doc.getTags().isEmpty()) {
            metadata.setTags(doc.getTags().stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }
    }

    private boolean tryFetchDetailedMoods(String bookId, BookMetadata metadata) {
        try {
            Integer bookIdInt = Integer.parseInt(bookId);
            HardcoverBookDetails details = hardcoverBookSearchService.fetchBookDetails(bookIdInt);
            if (details == null || details.getCachedTags() == null || details.getCachedTags().isEmpty()) {
                return false;
            }

            Set<String> filteredMoods = HardcoverMoodFilter.filterMoodsWithCounts(details.getCachedTags());
            if (!filteredMoods.isEmpty()) {
                metadata.setMoods(filteredMoods.stream()
                        .map(WordUtils::capitalizeFully)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            }

            Set<String> filteredGenres = HardcoverMoodFilter.filterGenresWithCounts(details.getCachedTags());
            if (!filteredGenres.isEmpty()) {
                metadata.setCategories(filteredGenres.stream()
                        .map(WordUtils::capitalizeFully)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            }

            Set<String> filteredTags = HardcoverMoodFilter.filterTagsWithCounts(details.getCachedTags());
            if (!filteredTags.isEmpty()) {
                metadata.setTags(filteredTags.stream()
                        .map(WordUtils::capitalizeFully)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            }

            return !filteredMoods.isEmpty();
        } catch (Exception e) {
            log.debug("Failed to fetch book details: {}", e.getMessage());
            return false;
        }
    }

    private void mapIsbns(GraphQLResponse.Document doc, FetchMetadataRequest request, BookMetadata metadata) {
        if (doc.getIsbns() == null) {
            return;
        }

        String inputIsbn = request.getIsbn();
        String matchingIsbn = null;
        if (StringUtils.isBlank(inputIsbn)) {
            // If we didn't search by ISBN, use first ISBN from results
            matchingIsbn = doc.getIsbns().stream()
                    .filter(isbn -> isbn.length() == 10 || isbn.length() == 13)
                    .findFirst()
                    .orElse(null);
        } else if (doc.getIsbns().contains(inputIsbn)) {
            // If we searched by ISBN and it matches a result perfectly, use that
            matchingIsbn = inputIsbn;
        } else {
            // If we searched by ISBN but got no exact matches, get response ISBN that most closely matches it
            LevenshteinDistance distance = LevenshteinDistance.getDefaultInstance();
            int smallestDistance = Integer.MAX_VALUE;
            for (String isbn : doc.getIsbns()) {
                if (isbn.length() != 10 && isbn.length() != 13) {
                    continue;
                }
                int currentDistance = distance.apply(isbn, inputIsbn);
                if (smallestDistance > currentDistance) {
                    smallestDistance = currentDistance;
                    matchingIsbn = isbn;
                }
            }
        }

        // Whatever ISBN we end up with, calculate the other one
        if (matchingIsbn != null && matchingIsbn.length() == 10) {
            metadata.setIsbn10(matchingIsbn);
            metadata.setIsbn13(BookUtils.isbn10To13(matchingIsbn));
        } else if (matchingIsbn != null && matchingIsbn.length() == 13) {
            metadata.setIsbn10(BookUtils.isbn13to10(matchingIsbn));
            metadata.setIsbn13(matchingIsbn);
        } else {
            // Can only happen if doc.getIsbns() is empty or doesn't have any 10/13 length strings
            metadata.setIsbn10(null);
            metadata.setIsbn13(null);
        }
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> bookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return bookMetadata.isEmpty() ? null : bookMetadata.getFirst();
    }
}
