package org.booklore.service.metadata.parser;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.WordUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import org.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import org.booklore.service.metadata.parser.hardcover.HardcoverMoodFilter;
import org.booklore.util.BookUtils;
import org.booklore.util.LanguageNormalizer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@AllArgsConstructor
public class HardcoverParser implements BookParser {
    private final HardcoverBookSearchService hardcoverBookSearchService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {

        //Try search by Isbn
        List<BookMetadata> metadata = searchByIsbn(fetchMetadataRequest);
        if (!metadata.isEmpty()) {
            return metadata;
        }

        // Else Search by Title/Author
        List<GraphQLResponse.Document> docs = searchByTitle(fetchMetadataRequest);

        // Then Search by hardcover id for any returned search results
        List<GraphQLResponse.BookWithEditions> results = searchById(docs, fetchMetadataRequest);

        // further filter editions of returned books
        results = filterEditions(results, book);
        return processBooks(results);
    }

    private List<BookMetadata> searchByIsbn(FetchMetadataRequest request) {

        String cleanedIsbn = ParserUtils.cleanIsbn(request.getIsbn());
        if (cleanedIsbn == null || cleanedIsbn.isBlank()) {
            return Collections.emptyList();
        }
        List<String> isbnCleaned = List.of(cleanedIsbn);

        log.info("Hardcover: Fetching metadata using ISBN {}", isbnCleaned.get(0));
        List<GraphQLResponse.BookWithEditions> hits = hardcoverBookSearchService.searchBookByIsbn(isbnCleaned);
        return processBooks(hits);
    }

    private List<BookMetadata> processBooks(List<GraphQLResponse.BookWithEditions> books) {
        if (books == null || books.isEmpty()) {
            return Collections.emptyList();
        }

        List<BookMetadata> results = new ArrayList<>();
        for (GraphQLResponse.BookWithEditions book : books) {
            if (book.getEditions() == null || book.getEditions().isEmpty()) {
                continue;
            }
            results.add(mapBookToMetadata(book, book.getEditions().getFirst()));
        }
        return results;
    }

    private List<GraphQLResponse.Document> searchByTitle(FetchMetadataRequest fetchMetadataRequest) {
        List<GraphQLResponse.Document> results = new ArrayList<>();
        String title = fetchMetadataRequest.getTitle();

        if (title == null || title.isBlank()) {
            log.warn("Hardcover: No title provided for search");
            return Collections.emptyList();
        }
        String author = fetchMetadataRequest.getAuthor();

        // 1. Try Title + Author
        if (author != null && !author.isBlank()) {
            author = formatRequestAuthor(author);
            log.info("Hardcover: Searching with title+author: '{} {}'", title, author);
            List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(title, author);
            results = filterSearch(hits, fetchMetadataRequest);
        }

        // 2. If no valid results found (or no author provided), Try Title only
        if (results.isEmpty()) {
            log.info("Hardcover: Searching with title only: '{}'", title);
            List<GraphQLResponse.Hit> hits = hardcoverBookSearchService.searchBooks(title);
            results = filterSearch(hits, fetchMetadataRequest);
        }

        // 3. Return empty if no results
        if (results.isEmpty()) {
            log.info("No results searching: {} {}", title, author);
            return Collections.emptyList();
        }
        return results;
    }

    private String formatRequestAuthor(String input) {
        if (input == null) {
            return "";
        }
        String[] authorArray = input.split(", ");
        input = authorArray.length > 0 ? authorArray[0] : "";
        return input;
    }

    private List<GraphQLResponse.Document> filterSearch(List<GraphQLResponse.Hit> hits, FetchMetadataRequest request) {
        if (hits == null || hits.isEmpty()) {
            log.info("Hardcover: No results found for title '{}'", request.getTitle());
            return Collections.emptyList();
        }

        String searchTitle = request.getTitle() != null ? request.getTitle() : "";
        String searchAuthor = request.getAuthor() != null ? formatRequestAuthor(request.getAuthor()) : "";

        //convert hits into document format.
        Stream<GraphQLResponse.Document> docs = hits.stream()
                .map(GraphQLResponse.Hit::getDocument)
                .filter(Objects::nonNull);

        log.debug("Filtering by title: '{}', author: '{}'", searchTitle, searchAuthor);

        if (!searchTitle.isBlank()) {
            docs = docs
                .map(mapTitleLevenshtein(searchTitle))
                .sorted(Comparator.comparingDouble(ScoredDocument::score))
                .map(ScoredDocument::document);
            log.debug("Filtered by title: {}", searchTitle);
        }

        if (!searchAuthor.isBlank()) {
            docs = docs
                    .map(mapAuthorLevenshtien(searchAuthor))
                    .sorted(Comparator.comparingDouble(ScoredDocument::score))
                    .filter(doc -> doc.score <= (searchAuthor.length() * 0.8))
                    .map(ScoredDocument::document);
        }

        return docs.toList();
    }

    private Function<GraphQLResponse.Document, ScoredDocument> mapTitleLevenshtein(String searchTitle) {
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
        String searchTitleLower = searchTitle.toLowerCase();

        return (GraphQLResponse.Document doc) -> {
            if (doc.getTitle() == null || doc.getTitle().isBlank()) {
                return new ScoredDocument(doc, Double.MAX_VALUE);
            }
            // calculate the lev.dist for the Work-level title and the search provided term
            double totalScore = levenshtein.apply(searchTitleLower, doc.getTitle().toLowerCase());

            if (doc.getAlternativeTitles() != null) {
                //repeat the lev.dist calc for each of the alternative titles
                totalScore += doc.getAlternativeTitles()
                        .stream()
                        .map(String::toLowerCase)
                        .map(title -> levenshtein.apply(searchTitleLower, title))
                        .min(Double::compare)
                        .orElse(Integer.MAX_VALUE);
            }

            // (minTitleDist+minAltTitleDist)/1+userCount. best scores should approach 0
            int usersCount = doc.getUsersCount() == null ? 0 : doc.getUsersCount();
            return new ScoredDocument(doc, (totalScore + 1)/(usersCount + 1));
        };
    }

    private Function<GraphQLResponse.Document, ScoredDocument> mapAuthorLevenshtien(String searchAuthor) {
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
        String searchAuthorLowercase = searchAuthor.toLowerCase();

        return (GraphQLResponse.Document doc) -> {
            if (doc.getAuthorNames() == null || doc.getAuthorNames().isEmpty()) {
                return new ScoredDocument(doc, Double.MAX_VALUE);
            }

            List<String> normalizedAuthors = doc.getAuthorNames().stream()
                    .map(String::toLowerCase)
                    .toList();

            //Find the author in the list of authors with the closest name to the search term
            double minDistance = normalizedAuthors.stream()
                    .map(author -> levenshtein.apply(searchAuthorLowercase, author))
                    .min(Double::compare)
                    .orElse(Integer.MAX_VALUE);

            return new ScoredDocument(doc, minDistance);
        };
    }

    private List<GraphQLResponse.BookWithEditions> searchById(List<GraphQLResponse.Document> docs, FetchMetadataRequest request) {
        if (docs == null || docs.isEmpty()) {
            log.warn("No documents provided for search by ID.");
            return Collections.emptyList();
        }
        // Extract hcid values from the docs
        List<Integer> hcid = docs.stream()
                .map(doc -> {
                    try {
                        return Integer.parseInt(doc.getId());
                    }
                    catch (NumberFormatException e) {
                        log.warn("Invalid ID: {}", doc.getId(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        log.info("Searching by Hardcover ID for {}", request.getTitle());
        List<GraphQLResponse.BookWithEditions> results = hardcoverBookSearchService.searchBookByHcid(hcid);

        Map<Integer, GraphQLResponse.BookWithEditions> bookMap = results.stream()
                .collect(Collectors.toMap(
                    GraphQLResponse.BookWithEditions::getId,
                    Function.identity()
                ));

        // Return results in the same order as (hcid) recived
        return hcid.stream()
                .map(bookMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<GraphQLResponse.BookWithEditions> filterEditions(List<GraphQLResponse.BookWithEditions> results, Book book) {
        return results.stream()
                .map(result -> {
                    // filter by format
                    List<GraphQLResponse.Edition> filteredByFormat = filterEditionsByFormat(result.getEditions(), book);

                    // filter by language
                    List<GraphQLResponse.Edition> filteredByLanguage = filterEditionsByLanguage(filteredByFormat);

                    // remove editions with no isbn
                    List<GraphQLResponse.Edition> filteredByISBN = filterEditionsByISBN(filteredByLanguage);

                    result.setEditions(filteredByISBN);
                    return result;
                })
                .toList();
    }

    private List<GraphQLResponse.Edition> filterEditionsByFormat(List<GraphQLResponse.Edition> editions, Book book)
    {
        if (book.getPrimaryFile() == null){
            return editions;
        }

        boolean isAudiobook = book.getPrimaryFile().getBookType().equals(BookFileType.AUDIOBOOK);
        List<GraphQLResponse.Edition> audiobooks = new ArrayList<>();
        List<GraphQLResponse.Edition> hardcovers = new ArrayList<>();

        for (GraphQLResponse.Edition edition : editions) {
            if (edition.getReadingFormatId() == 2) {
                audiobooks.add(edition);
            } else {
                hardcovers.add(edition);
            }
        }

        return isAudiobook && !audiobooks.isEmpty()
            ? audiobooks
            : hardcovers;
    }

    private List<GraphQLResponse.Edition> filterEditionsByLanguage(List<GraphQLResponse.Edition> result){
        String localeLanguage = Locale.getDefault().getLanguage();

        if (result == null || result.isEmpty()) {
            return result;
        }

        List<GraphQLResponse.Edition> filteredEditions = result.stream()
                .filter(edition -> {
                    String languageCode = edition.getLanguage() != null
                            ? edition.getLanguage().getCode2()
                            : null;
                    return languageCode != null && languageCode.equals(localeLanguage);
                })
                .toList();

        return !filteredEditions.isEmpty()
            ? filteredEditions
            : result;
    }

    private List<GraphQLResponse.Edition> filterEditionsByISBN(List<GraphQLResponse.Edition> result){
        if (result == null || result.isEmpty()) {
            return result;
        }

        List<GraphQLResponse.Edition> filteredEditions = result.stream()
                .filter(edition -> edition.getIsbn10() != null || edition.getIsbn13() != null)
                .toList();

        return !filteredEditions.isEmpty()
            ?   filteredEditions
            :   result;
    }

    private BookMetadata mapBookToMetadata(GraphQLResponse.BookWithEditions book, GraphQLResponse.Edition edition) {
        var builder = BookMetadata.builder();

        if (edition.getSubtitle() != null && book.getTitle().contains(edition.getSubtitle())) {
            book.setTitle(book.getTitle().replace(": " + book.getSubtitle(), ""));
        }

        builder.hardcoverId(book.getSlug())
                .title(book.getTitle())
                .subtitle(edition.getSubtitle())
                .pageCount(book.getPages())
                .description(book.getDescription())
                .hardcoverReviewCount(book.getReviewsCount())
                .thumbnailUrl(book.getImage() != null ? book.getImage().getUrl() : null)
                .provider(MetadataProvider.Hardcover);

        mapBookId(builder, book);
        mapCachedContributors(builder, book);
        mapReleaseDate(builder, book);
        mapSeriesData(builder, book);
        mapRating(builder, book);

        GraphQLResponse.CachedTags cachedTags = book.getCachedTags();

        mapMoods(builder, cachedTags);
        mapCategories(builder, cachedTags);
        mapTags(builder, cachedTags);
        mapSeriesInfo(builder, book);

        mapIsbn(builder, edition);
        mapLanguage(builder, edition);
        mapPublisher(builder, edition);
        mapEditionReleaseDate(builder, edition);

        return builder.build();
    }

    private void mapBookId(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.BookWithEditions book) {
        if (book.getId() != null) {
            builder.hardcoverBookId(book.getId().toString());
        }
    }

    private void mapSeriesData(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.BookWithEditions book) {
        if (book.getFeaturedBookSeries() != null && book.getFeaturedBookSeries().getSeries() != null) {
            builder.seriesName(book.getFeaturedBookSeries().getSeries().getName());
            builder.seriesTotal(book.getFeaturedBookSeries().getSeries().getPrimaryBooksCount());

            if (book.getFeaturedBookSeries().getPosition() != null) {
                try {
                    builder.seriesNumber(Float.parseFloat(String.valueOf(book.getFeaturedBookSeries()
                            .getPosition())));
                } catch (NumberFormatException _) {
                    // Handle the case where the series number cannot be parsed as a float
                }
            }
        }
    }

    private void mapRating(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.BookWithEditions book) {
        if (book.getRating() != null) {
            builder.hardcoverRating(
                    BigDecimal.valueOf(book.getRating())
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue());
        }
    }

    private void mapReleaseDate(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.BookWithEditions book) {
        if (book.getReleaseDate() != null) {
            try {
                builder.publishedDate(LocalDate.parse(book.getReleaseDate()));
            } catch (Exception _) {
                log.debug("Could not parse release date: {}", book.getReleaseDate());
            }
        }
    }

    private void mapSeriesInfo(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.BookWithEditions book) {
        if (book.getFeaturedBookSeries() == null || book.getFeaturedBookSeries().getSeries() == null) {

                return;
        }
        builder.seriesName(book.getFeaturedBookSeries().getSeries().getName());
        builder.seriesTotal(book.getFeaturedBookSeries().getSeries().getPrimaryBooksCount());

        if (book.getFeaturedBookSeries().getPosition() != null) {
            try {
                builder.seriesNumber(Float.parseFloat(String.valueOf(book.getFeaturedBookSeries().getPosition())));
            } catch (NumberFormatException _) {
                // Ignore parsing error if the series position is not a valid number
            }
        }
    }

    private void mapMoods(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.CachedTags cachedTags) {
        if (cachedTags != null && cachedTags.getMood() != null && !cachedTags.getMood().isEmpty()) {
            Set<String> basicFilteredMoods = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags.getMood());
            builder.moods(basicFilteredMoods.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
    }

    private void mapCategories(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.CachedTags cachedTags) {
        if (cachedTags != null && cachedTags.getGenre() != null && !cachedTags.getGenre().isEmpty()) {
            Set<String> filteredGenres = HardcoverMoodFilter.filterGenresWithCounts(cachedTags.getGenre());
            builder.categories(filteredGenres.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }
    }

    private void mapTags(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.CachedTags cachedTags) {
        if (cachedTags != null && cachedTags.getTag() != null && !cachedTags.getTag().isEmpty()) {
            Set<String> filteredTags = HardcoverMoodFilter.filterTagsWithCounts(cachedTags.getTag());
            builder.tags(filteredTags.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }
    }

    private void mapCachedContributors(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.BookWithEditions book){
        if (book.getCachedContributors() != null) {
            builder.authors(book.getCachedContributors().stream()
                    .map(GraphQLResponse.Contributor::getAuthor)
                    .filter(Objects::nonNull)
                    .map(GraphQLResponse.Author::getName)
                    .filter(Objects::nonNull)
                    .toList());
        }
    }

    private void mapEditionReleaseDate(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.Edition edition) {
        if (edition.getReleaseDate() != null) {
            try {
                builder.publishedDate(LocalDate.parse(edition.getReleaseDate()));
            } catch (Exception _) {
                log.debug("Could not parse release date: {}", edition.getReleaseDate());
            }
        }
    }

    private void mapIsbn(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.Edition edition) {
        builder.isbn10(edition.getIsbn10());
        builder.isbn13(edition.getIsbn13());

        if (edition.getIsbn10() != null && edition.getIsbn13() == null) {
            builder.isbn13(BookUtils.isbn10To13(edition.getIsbn10()));
        } else if (edition.getIsbn13() != null && edition.getIsbn10() == null) {
            builder.isbn10(BookUtils.isbn13to10(edition.getIsbn13()));
        }
    }

    private void mapLanguage(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.Edition edition) {
        if (edition.getLanguage() != null && edition.getLanguage().getCode2() != null) {
            builder.language(LanguageNormalizer.normalize(edition.getLanguage().getCode2()));
        }
    }

    private void mapPublisher(BookMetadata.BookMetadataBuilder builder, GraphQLResponse.Edition edition) {
        if (edition.getPublisher() != null && edition.getPublisher().getName() != null) {
            builder.publisher(edition.getPublisher().getName());
        }
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> bookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return bookMetadata.isEmpty() ? null : bookMetadata.getFirst();
    }

    record ScoredDocument (
            GraphQLResponse.Document document,
            double score
    ) {}
}