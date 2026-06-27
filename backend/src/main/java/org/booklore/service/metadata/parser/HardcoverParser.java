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
        filterEditions(results, book);
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

            BookMetadata metadata = new BookMetadata();
            log.debug("Processing edition '{}' with id '{}' of book '{}'", book.getEditions().getFirst().getTitle(),
                    book.getEditions().getFirst().getId(),
                    book.getTitle());
            mapBookToMetadata(book, book.getEditions().getFirst(), metadata);

            if (metadata != null) {
                results.add(metadata);
            }
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
        List<GraphQLResponse.Document> docs = hits.stream()
                .map(GraphQLResponse.Hit::getDocument)
                .filter(Objects::nonNull)
                .toList();

        log.debug("Filtering by title: '{}', author: '{}'", searchTitle, searchAuthor);
        if (!searchTitle.isBlank()) {
            docs = filterTitle(docs, searchTitle);
            log.debug("Filtered by title: {} ({} results)", searchTitle, docs.size());
        }

        if (!searchAuthor.isBlank()) {
            docs = filterAuthor(docs, searchAuthor);
            log.debug("Filtered by author: {} ({} results)", searchAuthor, docs.size());
        }

        // Sort the search results in order of their calculated lev-dist (asc).
        return docs.stream()
                .sorted(Comparator.comparingDouble(GraphQLResponse.Document::getLevenshteinDistanceTitle))
                .toList();
    }

    private List<GraphQLResponse.Document> filterAuthor(List<GraphQLResponse.Document> docs, String searchAuthor) {
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
        String searchAuthorLowercase = searchAuthor.toLowerCase();
        for (GraphQLResponse.Document doc : docs) {
            if (doc.getAuthorNames() == null || doc.getAuthorNames().isEmpty()) {
                doc.setLevenshteinDistanceAuthor(Double.MAX_VALUE);
                continue;
            }
            List<String> normalizedAuthors = doc.getAuthorNames().stream()
                    .map(String::toLowerCase)
                    .toList();

            //Find the author in the list of authors with the closest name to the search term
            double minDistance = normalizedAuthors.stream()
                    .map(author -> levenshtein.apply(searchAuthorLowercase, author))
                    .min(Double::compare)
                    .orElse(Integer.MAX_VALUE);

            doc.setLevenshteinDistanceAuthor(minDistance);
        }

        // find the edition with the lowest dist
        final double best = docs.stream()
                .mapToDouble(GraphQLResponse.Document::getLevenshteinDistanceAuthor)
                .min()
                .orElse(Double.MAX_VALUE);


        double threshold = searchAuthor.length()*0.8;

        //Try to return the searches where the distance is no greater than aproximately the length of the search term
        return best > threshold
            ? Collections.emptyList()
            : docs.stream()
            .filter(doc -> doc.getLevenshteinDistanceAuthor() <= threshold)
            .toList();
    }

    private List<GraphQLResponse.Document> filterTitle(List<GraphQLResponse.Document> docs, String searchTitle) {
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

        String searchTitleLower = searchTitle.toLowerCase();
        for (GraphQLResponse.Document doc : docs) {
            if (doc.getTitle() == null || doc.getTitle().isBlank()) {
                doc.setLevenshteinDistanceTitle(Double.MAX_VALUE);
                continue;
            }
            // calculate the lev.dist for the Work-level title and the search provided term
            double totalScore = levenshtein.apply(searchTitleLower, doc.getTitle().toLowerCase());

            if (doc.getAlternativeTitles() != null) {
                List<String> normalizedTitles = doc.getAlternativeTitles().stream()
                        .map(String::toLowerCase)
                        .toList();
                //repeart the lev.dist calc for each of the alternative titles
                totalScore += normalizedTitles.stream()
                        .map(title -> levenshtein.apply(searchTitleLower, title))
                        .min(Double::compare)
                        .orElse(Integer.MAX_VALUE);
            }

            // (minTitleDist+minAltTitleDist)/1+userCount. best scores should approach 0
            int usersCount = doc.getUsersCount() == null ? 0 : doc.getUsersCount();
            doc.setLevenshteinDistanceTitle((totalScore + 1)/(usersCount + 1));
        }
        return docs.stream().toList();
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
        for (GraphQLResponse.BookWithEditions result : results) {
            filterEditionsByFormat(results, result, book);
            filterEditionsByLanguage(results, result);
        }
        return results;
    }

    private List<GraphQLResponse.BookWithEditions> filterEditionsByFormat(List<GraphQLResponse.BookWithEditions> results, GraphQLResponse.BookWithEditions result, Book book)
    {
        if (book.getPrimaryFile() == null){
            return results;
        }

        boolean isAudiobook = book.getPrimaryFile().getBookType().equals(BookFileType.AUDIOBOOK);
        List<GraphQLResponse.Edition> audiobooks = new ArrayList<>();
        List<GraphQLResponse.Edition> hardcovers = new ArrayList<>();

        for (GraphQLResponse.Edition edition : result.getEditions()) {
            if (edition.getReadingFormatId() == 2) {
                audiobooks.add(edition);
            } else {
                hardcovers.add(edition);
            }
        }

        if (isAudiobook && !audiobooks.isEmpty()) {
            result.setEditions(audiobooks);
        }
        else{
            result.setEditions(hardcovers);
        }

        return results;
    }

    private List<GraphQLResponse.BookWithEditions> filterEditionsByLanguage(List<GraphQLResponse.BookWithEditions> results, GraphQLResponse.BookWithEditions result){
        String localeLanguage = Locale.getDefault().getLanguage();

        if (result.getEditions() == null || result.getEditions().isEmpty()) {
            return results;
        }

        List<GraphQLResponse.Edition> filteredEditions = result.getEditions().stream()
                .filter(edition -> {
                    String languageCode = edition.getLanguage() != null
                            ? edition.getLanguage().getCode2()
                            : null;
                    return languageCode != null && languageCode.equals(localeLanguage);
                })
                .toList();

        if (!filteredEditions.isEmpty()){
            result.setEditions(filteredEditions);
        }

        return results;
    }

    private BookMetadata mapBookToMetadata(GraphQLResponse.BookWithEditions book, GraphQLResponse.Edition edition, BookMetadata metadata) {

        metadata.setHardcoverId(book.getSlug());

        mapBookId(metadata, book);

        if (edition.getSubtitle() != null && book.getTitle().contains(edition.getSubtitle())) {
            book.setTitle(book.getTitle().replace(": " + book.getSubtitle(), ""));
        }

        metadata.setTitle(book.getTitle());
        metadata.setSubtitle(edition.getSubtitle());
        metadata.setPageCount(book.getPages());

        mapCachedContributors(metadata, book);
        mapReleaseDate(metadata, book);
        mapSeiesData(metadata, book);
        mapRating(metadata, book);

        metadata.setDescription(book.getDescription());
        metadata.setHardcoverReviewCount(book.getRatingsCount());

        GraphQLResponse.CachedTags cachedTags = book.getCachedTags();
        mapMoods(metadata, cachedTags);
        mapCategories(metadata, cachedTags);
        mapTags(metadata, cachedTags);
        mapSeriesInfo(metadata, book);

        metadata.setThumbnailUrl(book.getImage() != null ? book.getImage().getUrl() : null);
        metadata.setProvider(MetadataProvider.Hardcover);

        mapEditions(edition, metadata);

        return metadata;
    }

    private BookMetadata mapEditions(GraphQLResponse.Edition edition,
            BookMetadata metadata) {

        if (metadata.getIsbn13() == null || metadata.getIsbn13().isBlank()) {
            mapIsbn(metadata, edition);
        }
        if (metadata.getLanguage() == null || metadata.getLanguage().isBlank()) {
            mapLanguage(metadata, edition);
        }
        if (metadata.getPublisher() == null || metadata.getPublisher().isBlank() ) {
            mapPublisher(metadata, edition);
        }
        if (metadata.getPublishedDate() == null) {
            mapEditionReleaseDate(metadata, edition);
        }
        if (metadata.getAuthors() == null || metadata.getAuthors().isEmpty()) {
            mapCachedContributors(metadata, edition);
        }
        return metadata;
    }

    private void mapBookId(BookMetadata metadata, GraphQLResponse.BookWithEditions book) {
        Integer bookId = book.getId();
        if (bookId != null) {
            metadata.setHardcoverBookId(bookId.toString());
        }
    }

    private void mapSeiesData(BookMetadata metadata, GraphQLResponse.BookWithEditions book) {
        if (book.getFeaturedBookSeries() != null && book.getFeaturedBookSeries().getSeries() != null) {
            metadata.setSeriesName(book.getFeaturedBookSeries().getSeries().getName());
            metadata.setSeriesTotal(book.getFeaturedBookSeries().getSeries().getPrimaryBooksCount());

            if (book.getFeaturedBookSeries().getPosition() != null) {
                try {
                    metadata.setSeriesNumber(Float.parseFloat(String.valueOf(book.getFeaturedBookSeries()
                            .getPosition())));
                } catch (NumberFormatException _) {
                    // Handle the case where the series number cannot be parsed as a float
                }
            }
        }
    }

    private void mapRating(BookMetadata metadata, GraphQLResponse.BookWithEditions book) {
        if (book.getRating() != null) {
            metadata.setHardcoverRating(
                    BigDecimal.valueOf(book.getRating())
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue());
        }
    }

    private void mapReleaseDate(BookMetadata metadata, GraphQLResponse.BookWithEditions book) {
        if (book.getReleaseDate() != null) {
            try {
                metadata.setPublishedDate(LocalDate.parse(book.getReleaseDate()));
            } catch (Exception _) {
                log.debug("Could not parse release date: {}", book.getReleaseDate());
            }
        }
    }

    private void mapSeriesInfo(BookMetadata metadata, GraphQLResponse.BookWithEditions book) {
        if (book.getFeaturedBookSeries() == null || book.getFeaturedBookSeries().getSeries() == null) {

                return;
        }
        metadata.setSeriesName(book.getFeaturedBookSeries().getSeries().getName());
        metadata.setSeriesTotal(book.getFeaturedBookSeries().getSeries().getPrimaryBooksCount());

        if (book.getFeaturedBookSeries().getPosition() != null) {
            try {
                metadata.setSeriesNumber(Float.parseFloat(String.valueOf(book.getFeaturedBookSeries().getPosition())));
            } catch (NumberFormatException _) {
                // Ignore parsing error if the series position is not a valid number
            }
        }
    }

    private void mapMoods(BookMetadata metadata, GraphQLResponse.CachedTags cachedTags) {
        if (cachedTags != null && cachedTags.getMood() != null && !cachedTags.getMood().isEmpty()) {
            Set<String> basicFilteredMoods = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags.getMood());
            metadata.setMoods(basicFilteredMoods.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
    }

    private void mapCategories(BookMetadata metadata, GraphQLResponse.CachedTags cachedTags) {
        if (cachedTags != null && cachedTags.getGenre() != null && !cachedTags.getGenre().isEmpty()) {
            Set<String> filteredGenres = HardcoverMoodFilter.filterGenresWithCounts(cachedTags.getGenre());
            metadata.setCategories(filteredGenres.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }
    }

    private void mapTags(BookMetadata metadata, GraphQLResponse.CachedTags cachedTags) {
        if (cachedTags != null && cachedTags.getTag() != null && !cachedTags.getTag().isEmpty()) {
            Set<String> filteredTags = HardcoverMoodFilter.filterTagsWithCounts(cachedTags.getTag());
            metadata.setTags(filteredTags.stream()
                    .map(WordUtils::capitalizeFully)
                    .collect(Collectors.toSet()));
        }
    }

    private void mapCachedContributors(BookMetadata metadata, GraphQLResponse.BookWithEditions book){
        if (book.getCachedContributors() != null) {
            metadata.setAuthors(book.getCachedContributors().stream()
                    .map(GraphQLResponse.Contributor::getAuthor)
                    .filter(Objects::nonNull)
                    .map(GraphQLResponse.Author::getName)
                    .filter(Objects::nonNull)
                    .toList());
        }
    }

    private void mapCachedContributors(BookMetadata metadata, GraphQLResponse.Edition edition) {
        if (edition.getCachedContributors() != null) {
            metadata.setAuthors(edition.getCachedContributors().stream()
                    .map(GraphQLResponse.Contributor::getAuthor)
                    .filter(Objects::nonNull)
                    .map(GraphQLResponse.Author::getName)
                    .filter(Objects::nonNull)
                    .toList());
        }
    }

    private void mapEditionReleaseDate(BookMetadata metadata, GraphQLResponse.Edition edition) {
        if (edition.getReleaseDate() != null) {
            try {
                metadata.setPublishedDate(LocalDate.parse(edition.getReleaseDate()));
            } catch (Exception _) {
                log.debug("Could not parse release date: {}", edition.getReleaseDate());
            }
        }
    }

    private void mapIsbn(BookMetadata metadata, GraphQLResponse.Edition edition) {
        metadata.setIsbn10(edition.getIsbn10());
        metadata.setIsbn13(edition.getIsbn13());

        if (metadata.getIsbn10() != null && metadata.getIsbn13() == null) {
            metadata.setIsbn13(BookUtils.isbn10To13(edition.getIsbn10()));
        } else if (metadata.getIsbn13() != null && metadata.getIsbn10() == null) {
            metadata.setIsbn10(BookUtils.isbn13to10(edition.getIsbn13()));
        }
    }

    private void mapLanguage(BookMetadata metadata, GraphQLResponse.Edition edition) {
        if (edition.getLanguage() != null && edition.getLanguage().getCode2() != null) {
            metadata.setLanguage(LanguageNormalizer.normalize(edition.getLanguage().getCode2()));
        }
    }

    private void mapPublisher(BookMetadata metadata, GraphQLResponse.Edition edition) {
        if (edition.getPublisher() != null && edition.getPublisher().getName() != null) {
            metadata.setPublisher(edition.getPublisher().getName());
        }
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> bookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return bookMetadata.isEmpty() ? null : bookMetadata.getFirst();
    }
}