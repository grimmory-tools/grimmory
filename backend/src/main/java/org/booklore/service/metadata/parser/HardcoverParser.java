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
//    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
//    private static final double AUTHOR_MATCH_THRESHOLD = 0.5;
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
                .orElse(BookCategory.Hardcover);

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
//    private BookMetadata mapDocumentToMetadata(GraphQLResponse.Document doc, FetchMetadataRequest request, boolean fetchDetailedMoods) {
//        BookMetadata metadata = new BookMetadata();
//        metadata.setHardcoverId(doc.getSlug());
//
//        String bookId = parseBookId(doc.getId());
//        if (bookId != null) {
//            metadata.setHardcoverBookId(bookId);
//        }
//
//        metadata.setTitle(doc.getTitle());
//        metadata.setSubtitle(doc.getSubtitle());
//        metadata.setDescription(doc.getDescription());
//
//        if (doc.getAuthorNames() != null) {
//            metadata.setAuthors(List.copyOf(doc.getAuthorNames()).reversed()); // Maybe upstream this?
//        }
//
//        mapSeriesInfo(doc, metadata);
//
//        if (doc.getRating() != null) {
//            metadata.setHardcoverRating(
//                    BigDecimal.valueOf(doc.getRating()).setScale(2, RoundingMode.HALF_UP).doubleValue()
//            );
//        }
//        metadata.setHardcoverReviewCount(doc.getRatingsCount());
//        metadata.setPageCount(doc.getPages());
//
//        if (doc.getReleaseDate() != null) {
//            try {
//                metadata.setPublishedDate(LocalDate.parse(doc.getReleaseDate()));
//            } catch (Exception e) {
//                log.debug("Could not parse release date: {}", doc.getReleaseDate());
//            }
//        }
//
//        mapTagsAndMoods(doc, metadata, bookId, fetchDetailedMoods);
//
//        mapIsbns(doc, request, metadata);
//
//        metadata.setThumbnailUrl(doc.getImage() != null ? doc.getImage().getUrl() : null);
//        metadata.setProvider(MetadataProvider.Hardcover);
//        return metadata;
//    }
//    private BookMetadata mapBookToMetadata(GraphQLResponse.BookWithEditions book) {
//        BookMetadata metadata = new BookMetadata();
//
//        metadata.setHardcoverId(book.get);
//    }
    private BookMetadata mapBookToMetadata(GraphQLResponse.BookWithEditions book){
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

            if(edition == null){

                metadata.setTitle(book.getTitle());
                metadata.setSubtitle(book.getSubtitle());
                metadata.setPageCount(book.getPages());

                mapReleaseDate(metadata, book);
                //mapCachedContributors(metadata, book, edition); can't call this with edition
            }
            else{
                mapEditions(book, edition, metadata);
            }
        }
        else{
            mapEditions(book, edition, metadata);
        }
        return metadata;
    }

    private BookMetadata mapEditions(GraphQLResponse.BookWithEditions book, GraphQLResponse.Edition edition, BookMetadata metadata) {

        metadata.setTitle(edition.getTitle());
        metadata.setSubtitle(edition.getSubtitle());

        metadata.setPageCount(edition.getPages());
        mapIsbn(metadata, edition);

        mapLanguage(metadata,edition);
        mapPublisher(metadata, edition);

        mapEditionReleaseDate(metadata, edition);
        mapCachedContributors(metadata, book, edition);

        return metadata;
    }

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

    private void mapRating(BookMetadata metadata, GraphQLResponse.BookWithEditions book){
        if (book.getRating() != null) {
            metadata.setHardcoverRating(
                    BigDecimal.valueOf(book.getRating())
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue()
            );
        }
    }

    private void mapReleaseDate(BookMetadata metadata, GraphQLResponse.BookWithEditions book){
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

    private void mapCachedContributors(BookMetadata metadata, GraphQLResponse.BookWithEditions book, GraphQLResponse.Edition edition){
        if (book.getCachedContributors() != null) {
            metadata.setAuthors(edition.getCachedContributors().stream()
                    .map(GraphQLResponse.Contributor::getAuthor)
                    .filter(Objects::nonNull)
                    .map(GraphQLResponse.Author::getName)
                    .filter(Objects::nonNull)
                    .toList());
        }
    }

    private void mapEditionReleaseDate(BookMetadata metadata, GraphQLResponse.Edition edition){
        if (edition.getReleaseDate() != null) {
            try {
                metadata.setPublishedDate(LocalDate.parse(edition.getReleaseDate()));
            } catch (Exception _) {
                log.debug("Could not parse release date: {}", edition.getReleaseDate());
            }
        }
    }

    private void mapIsbn(BookMetadata metadata, GraphQLResponse.Edition edition){
        metadata.setIsbn10(edition.getIsbn10());
        metadata.setIsbn13(edition.getIsbn13());

        if (metadata.getIsbn10() != null && metadata.getIsbn13() == null) {
            metadata.setIsbn13(BookUtils.isbn10To13(edition.getIsbn10()));
        }
        else if (metadata.getIsbn13() != null && metadata.getIsbn10() == null) {
            metadata.setIsbn10(BookUtils.isbn13to10(edition.getIsbn13()));
        }
    }

    private void mapLanguage(BookMetadata metadata, GraphQLResponse.Edition edition){
        if (edition.getLanguage() != null && edition.getLanguage().getCode2() != null) {
            metadata.setLanguage(LanguageNormalizer.normalize(edition.getLanguage().getCode2()));
        }
    }

    private void mapPublisher(BookMetadata metadata, GraphQLResponse.Edition edition){
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
