package org.booklore.service.metadata.parser;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.WordUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.enums.BookCategory;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class HardcoverParser implements BookParser {
    private final HardcoverBookSearchService hardcoverBookSearchService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<String> isbnCleaned = new ArrayList<>();
        isbnCleaned.add(ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn()));

        boolean searchByIsbn = isbnCleaned.getFirst() != null && !isbnCleaned.getFirst().isBlank();
        if (searchByIsbn) {
            log.info("Hardcover: Fetching metadata using ISBN {}", isbnCleaned);
            List<GraphQLResponse.BookWithEditions> hits = hardcoverBookSearchService.searchBookByIsbn(isbnCleaned);
            return processBooks(hits);
        }
        // Search by Title/Author
        List<GraphQLResponse.Hit> hits = searchByTitle(fetchMetadataRequest);
        List<GraphQLResponse.Document> docs = filterSearchByTitle(hits, fetchMetadataRequest);
        List<GraphQLResponse.BookWithEditions> results = searchById(docs, fetchMetadataRequest);

        results = filterEditions(results, book);
        return processBooks(results);
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
                log.debug("Processing edition '{}' with id '{}' of book '{}'", edition.getTitle(), edition.getId(),
                        book.getTitle());
                BookMetadata metadata = mapBookToMetadata(book, edition);
                if (metadata != null) {
                    results.add(metadata);
                }
            }
        }
        return results;
    }

    private List<GraphQLResponse.Hit> searchByTitle(FetchMetadataRequest fetchMetadataRequest) {
        String title = fetchMetadataRequest.getTitle();
        if (title == null || title.isBlank()) {
            log.warn("Hardcover: No title provided for search");
            return Collections.emptyList();
        }
        // format the title
        title = Pattern.compile("[^a-zA-Z0-9\\s]+")
                .matcher(title.trim().toLowerCase()).replaceAll("");
        // search via title only
        log.info("Hardcover: Searching for title '{}'", title);
        return hardcoverBookSearchService.searchBooks(title);
    }

    private List<GraphQLResponse.Document> filterSearchByTitle(List<GraphQLResponse.Hit> hits, FetchMetadataRequest request) {
        if (hits == null || hits.isEmpty()) {
            log.info("Hardcover: No results found for title '{}'", request.getTitle());
            return Collections.emptyList();
        }

        String searchTitle = request.getTitle() != null ? request.getTitle() : "";
        String searchAuthor = request.getAuthor() != null ? request.getAuthor().trim() : "";

        //convert hits into document format.
        List<GraphQLResponse.Document> docs = hits.stream()
                .map(GraphQLResponse.Hit::getDocument)
                .toList();

        // Filter by author if not blank
        if (searchAuthor.isEmpty()) {
            docs = filterTitle(docs, searchTitle);
        } else {
            docs = filterAuthor(docs, searchAuthor);
            docs = filterTitle(docs, searchTitle);
        }

        // Order Collections with most isbn's at the top
        return sortSearchResultsByISBNCount(docs);
    }

    private List<GraphQLResponse.Document> filterAuthor(List<GraphQLResponse.Document> docs, String searchAuthor) {
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
        List<GraphQLResponse.Document> originalDocs = docs;

        for (GraphQLResponse.Document doc : docs) {
            int distance = levenshtein.apply(searchAuthor, doc.getAuthorNames().toString());
            doc.setLevenshteinDistance(distance);
        }
        docs = docs.stream()
                .sorted(Comparator.comparingInt(GraphQLResponse.Document::getLevenshteinDistance))
                .toList();

        final double best = docs.getFirst().getLevenshteinDistance();
        final double worst = docs.getLast().getLevenshteinDistance();
        final double threshold = Math.min(best, worst) + 1;

        List<GraphQLResponse.Document> newDocs = docs.stream()
                .filter(doc -> doc.getLevenshteinDistance() <= threshold)
                .toList();

        if (newDocs.isEmpty()) {
            return originalDocs;
        } else {
            return newDocs;
        }

    }

    private List<GraphQLResponse.Document> filterTitle(List<GraphQLResponse.Document> docs, String searchTitle) {
        List<GraphQLResponse.Document> originalDocs = docs;

        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

        for (GraphQLResponse.Document doc : docs) {
            int distance = levenshtein.apply(searchTitle, doc.getTitle());
            doc.setLevenshteinDistance(distance);
        }

        docs = docs.stream()
                .sorted(Comparator.comparingInt(GraphQLResponse.Document::getLevenshteinDistance))
                .toList();

        final double best = docs.getFirst().getLevenshteinDistance();
        final double worst = docs.getLast().getLevenshteinDistance();
        final double threshold = Math.min(best, worst) + 1;

        List<GraphQLResponse.Document> newDocs = docs.stream()
                .filter(doc -> doc.getLevenshteinDistance() <= threshold)
                .toList();

        if (newDocs.isEmpty()) {
            return originalDocs;
        }
        return newDocs;

    }

    private List<GraphQLResponse.Document> sortSearchResultsByISBNCount(List<GraphQLResponse.Document> docs) {
        return docs.stream()
                .sorted(Comparator.comparingInt(doc -> doc.getIsbns().size()))
                .toList()
                .reversed();
    }

    private List<GraphQLResponse.BookWithEditions> searchById(List<GraphQLResponse.Document> docs, FetchMetadataRequest request) {
        List<String> isbnList = new ArrayList<>();

        // for the top result, grab all isbns (all editions)
        GraphQLResponse.Document topMatch = docs.getFirst();
        if (topMatch.getIsbns() != null && !topMatch.getIsbns().isEmpty()) {
            isbnList.addAll(topMatch.getIsbns());
        }

        int hcid;
        //if the results contain no isbn's search by the hcid
        if ((isbnList == null || isbnList.isEmpty()) && (docs.getFirst().getId() != null && !docs.getFirst().getId().isEmpty())) {
            hcid = Integer.parseInt(docs.getFirst().getId());
            return hardcoverBookSearchService.searchBookByIsbn(hcid);
        }
        log.info("Searching by ISBN for {}'", request.getTitle());
        return hardcoverBookSearchService.searchBookByIsbn(isbnList);
    }

    private List<GraphQLResponse.BookWithEditions> filterEditions(List<GraphQLResponse.BookWithEditions> results,
            Book book) {
        List<GraphQLResponse.BookWithEditions> originalResults = new ArrayList<>(results);
        BookCategory category = null;
        try {
            category = BookFileType
                    .fromExtension(book.getPrimaryFile().getExtension())
                    .map(BookFileType::category)
                    .orElse(BookCategory.Hardcover);
        } catch (Exception _) {
            return results; //write this better?
        }

        int readingFormatId;
        switch (category) {
            case AUDIOBOOK -> readingFormatId = 2;
            case EBOOK -> readingFormatId = 4;
            default -> readingFormatId = 1;
        }
        for (GraphQLResponse.BookWithEditions result : results) {
            if (result.getEditions() == null || result.getEditions().isEmpty()) {
                continue;
            }
            List<GraphQLResponse.Edition> keep = new ArrayList<>();
            List<GraphQLResponse.Edition> hardcovers = new ArrayList<>();

            for (GraphQLResponse.Edition edition : result.getEditions()) {
                if (edition.getReadingFormatId() == readingFormatId) {
                    keep.add(edition);
                } else if (edition.getReadingFormatId() == 1) {
                    hardcovers.add(edition);
                }
            }
            List<GraphQLResponse.Edition> filtered = !keep.isEmpty() ? keep : hardcovers;
            result.setEditions(filtered);
        }
        if (results.stream().allMatch(r -> r.getEditions() == null || r.getEditions().isEmpty())) {
            return originalResults;
        }
        return results;
    }

    private BookMetadata mapBookToMetadata(GraphQLResponse.BookWithEditions book) {
        GraphQLResponse.Edition edition = new GraphQLResponse.Edition();
        return mapBookToMetadata(book, edition);
    }

    private BookMetadata mapBookToMetadata(GraphQLResponse.BookWithEditions book, GraphQLResponse.Edition edition) {

        BookMetadata metadata = new BookMetadata();
        metadata.setHardcoverId(book.getSlug());

        if (metadata.getTitle() == null) {

            mapBookId(metadata, book);
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

            if (edition == null) {

                metadata.setTitle(book.getTitle());
                metadata.setSubtitle(book.getSubtitle());
                metadata.setPageCount(book.getPages());

                mapReleaseDate(metadata, book);
                // mapCachedContributors(metadata, book, edition); can't call this with edition
            } else {
                mapEditions(book, edition, metadata);
            }
        } else {
            mapEditions(book, edition, metadata);
        }
        return metadata;
    }

    private BookMetadata mapEditions(GraphQLResponse.BookWithEditions book, GraphQLResponse.Edition edition,
            BookMetadata metadata) {

        metadata.setTitle(edition.getTitle());
        metadata.setSubtitle(edition.getSubtitle());

        metadata.setPageCount(edition.getPages());
        mapIsbn(metadata, edition);

        mapLanguage(metadata, edition);
        mapPublisher(metadata, edition);

        mapEditionReleaseDate(metadata, edition);
        mapCachedContributors(metadata, book, edition);

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
        if (book.getFeaturedBookSeries() == null) {
            return;
        }
        if (book.getFeaturedBookSeries() != null) {
            metadata.setSeriesName(book.getFeaturedBookSeries().getSeries().getName());
            metadata.setSeriesTotal(book.getFeaturedBookSeries().getSeries().getPrimaryBooksCount());
        }
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