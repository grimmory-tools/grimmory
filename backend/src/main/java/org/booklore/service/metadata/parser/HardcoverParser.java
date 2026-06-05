package org.booklore.service.metadata.parser;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.commons.text.similarity.FuzzyScore;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.BookCategory;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import org.booklore.service.metadata.parser.hardcover.HardcoverBookDetails;
import org.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import org.booklore.service.metadata.parser.hardcover.HardcoverMoodFilter;
import org.booklore.util.BookUtils;
import org.booklore.util.LanguageNormalizer;
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
        List<String> isbnCleaned = new ArrayList<>();
        isbnCleaned.add(ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn()));
        boolean searchByIsbn = isbnCleaned.getFirst() != null && !isbnCleaned.isEmpty();
        //searchByIsbn = false; //Testing variable.
        if (searchByIsbn) {
            log.info("Hardcover: Fetching metadata using ISBN {}", isbnCleaned);
            List<GraphQLResponse.BookWithEditions> hits = hardcoverBookSearchService.searchBookByIsbn(isbnCleaned);
            return processBooks(hits);
        }

        String title = fetchMetadataRequest.getTitle();

        if (title == null || title.isBlank()) {
            log.warn("Hardcover: No title provided for search");
            return Collections.emptyList();
        }
        else{
            title = Pattern.compile("[^a-zA-Z0-9\\s]+")
                    .matcher(title.trim().toLowerCase()).replaceAll("");
        }

        List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(title);
        List<BookMetadata> results = processHits(hits, fetchMetadataRequest, book);

        if (results.isEmpty()) {
            log.info("Hardcover: No results found for title '{}'", title);
            return results;
        }

        return results;
    }

    private List<BookMetadata> processHits(List<GraphQLResponse.Hit> hits, FetchMetadataRequest request, Book book) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }

        String searchTitle = request.getTitle() != null ? request.getTitle() : "";
        String searchAuthor = request.getAuthor() != null ? request.getAuthor().trim() : "";

        List<GraphQLResponse.Document> docs = hits.stream()
                .map(GraphQLResponse.Hit::getDocument)
                .toList();

        // Filter by author
        //need to start building redundacies
        List<GraphQLResponse.Document> matchedByTitles = new ArrayList<>();
        List<GraphQLResponse.Document> matchedByAuthors = new ArrayList<>();
        if (searchAuthor.isEmpty() || searchAuthor == null) {
            matchedByTitles = filterTitle(docs, searchTitle);
        }
        else {
            matchedByAuthors = filterAuthor(docs, searchAuthor);
            matchedByTitles = filterTitle(matchedByAuthors, searchTitle);
        }

        //if isbnList
        // Order Collections with most isbn's at the top
        matchedByTitles = sortSearchResultsByISBNCount(matchedByTitles); // test this for errors with no isbn route
        List<String> isbnList = new ArrayList<>();

        GraphQLResponse.Document topmatch = matchedByTitles.getFirst();
        if (topmatch.getIsbns() != null && !topmatch.getIsbns().isEmpty()) {
            isbnList.addAll(topmatch.getIsbns());
        }

        int hcid;
        List<GraphQLResponse.BookWithEditions> allResults = new ArrayList<>();

        if (matchedByTitles.getFirst().getIsbns().isEmpty()) {
            if (matchedByTitles.getFirst().getId() != null && !matchedByTitles.getFirst().getId().isEmpty()) {
                hcid = Integer.parseInt(matchedByTitles.getFirst().getId());
                allResults = hardcoverBookSearchService.searchBookByIsbn(hcid);
            }
        }
        else {
            allResults = hardcoverBookSearchService.searchBookByIsbn(isbnList);
        }

        allResults = filterEditions(allResults, book);

        return processBooks(allResults);
    }

    private List<BookMetadata> processBooks(List<GraphQLResponse.BookWithEditions> books) {
        if (books == null || books.isEmpty()) {
            return Collections.emptyList();
        }

        List<BookMetadata> results = new ArrayList<>();
        for (GraphQLResponse.BookWithEditions book : books) {
            if (book.getEditions() == null || book.getEditions().isEmpty()) {
                BookMetadata metadata = mapBookToMetadata(books.getFirst());
                results.add(metadata);
                return results;
            }
            for (GraphQLResponse.Edition edition : book.getEditions()) {
                log.debug("Processing edition '{}' with id '{}' of book '{}'", edition.getTitle(), edition.getId(), book.getTitle());
                BookMetadata metadata = mapBookToMetadata(book, edition);
                if (metadata != null) {
                    results.add(metadata);
                }
            }
        }
        return results;
    }

    private List<GraphQLResponse.Document> filterAuthor(List<GraphQLResponse.Document> docs, String searchAuthor) {
        LevenshteinDistance levenshtein = new LevenshteinDistance();
        List<GraphQLResponse.Document> originalDocs = docs;
//        String docAuthor;
        for (GraphQLResponse.Document doc : docs) {
            if(!doc.getAuthorNames().isEmpty()){
                doc.getAuthorNames().stream()
                        .findFirst()
                        .orElse("".trim()); //what??
            }
//            else {
//                docAuthor = "";
//            }
            int distance = levenshtein.apply(searchAuthor, doc.getAuthorNames().toString());
            doc.setLevenshteinDistance(distance);
        }
         docs = docs.stream()
                .sorted(Comparator.comparingInt(GraphQLResponse.Document::getLevenshteinDistance))
                .collect(Collectors.toList());

        final double best = docs.getFirst().getLevenshteinDistance();
        final double worst = docs.getLast().getLevenshteinDistance();
        final double threshold = Math.min(best, worst) + 1;

        List<GraphQLResponse.Document> newDocs = docs.stream()
                .filter(doc -> doc.getLevenshteinDistance() <= threshold)
                .collect(Collectors.toList());

        if (newDocs.isEmpty()){
            return  originalDocs;
        }
        else {
            return newDocs;
        }

    }

    private List<GraphQLResponse.Document> filterTitle(List<GraphQLResponse.Document> docs, String searchTitle) {
        List<GraphQLResponse.Document> originalDocs = docs;
//        List<GraphQLResponse.Document> newDocs = docs.stream()
//                .filter(doc -> doc.getTitle() != null && !doc.getTitle().isBlank())
//                .collect(Collectors.toList());

        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

        for (GraphQLResponse.Document doc : docs) {
            int distance = levenshteinDistance.apply(searchTitle, doc.getTitle());
            doc.setLevenshteinDistance(distance);
        }

        if (docs.isEmpty()) {
            return originalDocs;
        }

        // Remove docs above average distance, sort remainder by distance ascending
        return docs.stream()
                .filter(doc -> doc.getLevenshteinDistance() <= 5)
                .sorted(Comparator.comparingInt(GraphQLResponse.Document::getLevenshteinDistance))
                .toList();
    }

    private List<GraphQLResponse.Document>sortSearchResultsByISBNCount(List<GraphQLResponse.Document> docs){
        return docs.stream()
                .sorted(Comparator.comparingInt(doc -> doc.getIsbns().size()))
                .collect(Collectors.toList())
                .reversed();
    }

    private List<GraphQLResponse.BookWithEditions> filterEditions(List<GraphQLResponse.BookWithEditions> results, Book book) {
        List<GraphQLResponse.BookWithEditions> originalResults = new ArrayList<>(results);
        BookCategory category = BookFileType
            .fromExtension(book.getPrimaryFile().getExtension())
            .map(BookFileType::category)
            .orElse(null); // fix this

        int readingFormatId;
        switch (category) {
            case AUDIOBOOK -> readingFormatId = 2;
            case EBOOK -> readingFormatId = 4;
            default -> readingFormatId = 1;
        }
        Allresults.forEach(b -> filterEditions(b, readingFormatId));

        return processBooksWithEditions(Allresults);
    }

    private boolean filterAuthor(GraphQLResponse.Document doc, String searchAuthor,
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

    private boolean filterTitle(GraphQLResponse.Document doc, String searchTitle) {
        if (doc.getTitle() == null || doc.getTitle().isEmpty()) {
            return false;
        }

        List<String> searchTitleTokens = List.of(doc.getTitle());

        boolean topResult = false;
        List<GraphQLResponse.Document> matchedTitles = new ArrayList<>();
        for (String foo : searchTitleTokens) {
            if (foo.equalsIgnoreCase(searchTitle)) {
                topResult = true;
                return true;
            }
            if (foo.equalsIgnoreCase(searchTitle) && !topResult) {
                return true;
            }
        }
        return false;
    }

    private void filterEditions(GraphQLResponse.BookWithEditions bookWithEditions, int readingFormatId) {
        if (bookWithEditions.getEditions() == null || bookWithEditions.getEditions().isEmpty()) {
            return;
        }
        Map<Boolean, List<GraphQLResponse.Edition>> partitioned = bookWithEditions.getEditions()
                .stream()
                .filter(e -> e.getReadingFormatId() == readingFormatId || e.getReadingFormatId() == 1)
                .collect(Collectors.partitioningBy(e -> e.getReadingFormatId() == readingFormatId));

        List<GraphQLResponse.Edition> filtered = partitioned.get(true).isEmpty()
                ? partitioned.get(false)
                : partitioned.get(true);

        bookWithEditions.setEditions(filtered);
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
            metadata.setAuthors(List.copyOf(doc.getAuthorNames()).reversed()); // Maybe upstream this?
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
            metadata.setLanguage(LanguageNormalizer.normalize(edition.getLanguage().getCode2()));
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

        metadata.setThumbnailUrl(book.getImage() != null ? book.getImage().getUrl() : null);
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
            // If we searched by ISBN. and it matches a result perfectly, use that
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
