package com.adityachandel.booklore.service.opds;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.enums.OpdsSortOrder;
import com.adityachandel.booklore.service.MagicShelfService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpdsFeedService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final AuthenticationService authenticationService;
    private final OpdsBookService opdsBookService;
    private final MagicShelfService magicShelfService;
    private final MagicShelfBookService magicShelfBookService;

    public String generateRootNavigation(HttpServletRequest request) {
        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:root</id>
                  <title>Booklore Catalog</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now()));

        feed.append("""
                  <entry>
                    <title>All Books</title>
                    <id>urn:booklore:catalog:all</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                    <content type="text">Browse all available books</content>
                  </entry>
                """.formatted(now(), escapeXml("/api/v1/opds/catalog?page=1&size=" + DEFAULT_PAGE_SIZE)));

        feed.append("""
                  <entry>
                    <title>Recently Added</title>
                    <id>urn:booklore:catalog:recent</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                    <content type="text">Recently added books</content>
                  </entry>
                """.formatted(now(), escapeXml("/api/v1/opds/recent?page=1&size=" + DEFAULT_PAGE_SIZE)));

        feed.append("""
                  <entry>
                    <title>Libraries</title>
                    <id>urn:booklore:navigation:libraries</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="/api/v1/opds/libraries" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                    <content type="text">Browse books by library</content>
                  </entry>
                """.formatted(now()));

        feed.append("""
                  <entry>
                    <title>Shelves</title>
                    <id>urn:booklore:navigation:shelves</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="/api/v1/opds/shelves" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                    <content type="text">Browse your personal shelves</content>
                  </entry>
                """.formatted(now()));

        feed.append("""
                  <entry>
                    <title>Magic Shelves</title>
                    <id>urn:booklore:navigation:magic-shelves</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="/api/v1/opds/magic-shelves" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                    <content type="text">Browse your smart, dynamic shelves</content>
                  </entry>
                """.formatted(now()));

        feed.append("""
                  <entry>
                    <title>Authors</title>
                    <id>urn:booklore:navigation:authors</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="/api/v1/opds/authors" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                    <content type="text">Browse books by author</content>
                  </entry>
                """.formatted(now()));

        feed.append("""
                  <entry>
                    <title>Series</title>
                    <id>urn:booklore:navigation:series</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="/api/v1/opds/series" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                    <content type="text">Browse books by series</content>
                  </entry>
                """.formatted(now()));

        feed.append("""
                  <entry>
                    <title>Surprise Me</title>
                    <id>urn:booklore:catalog:surprise</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="/api/v1/opds/surprise" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                    <content type="text">25 random books from the catalog</content>
                  </entry>
                """.formatted(now()));

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateLibrariesNavigation(HttpServletRequest request) {
        Long userId = getUserId();
        List<Library> libraries = opdsBookService.getAccessibleLibraries(userId);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:navigation:libraries</id>
                  <title>Libraries</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds/libraries" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now()));

        for (Library library : libraries) {
            feed.append("""
                      <entry>
                        <title>%s</title>
                        <id>urn:booklore:library:%d</id>
                        <updated>%s</updated>
                        <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                        <content type="text">%s</content>
                      </entry>
                    """.formatted(
                    escapeXml(library.getName()),
                    library.getId(),
                    now(),
                    escapeXml("/api/v1/opds/catalog?libraryId=" + library.getId()),
                    escapeXml(library.getName() != null ? library.getName() : "Library collection")
            ));
        }

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateShelvesNavigation(HttpServletRequest request) {
        Long userId = getUserId();

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:navigation:shelves</id>
                  <title>Shelves</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds/shelves" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now()));

        if (userId != null) {
            var shelves = opdsBookService.getUserShelves(userId);

            if (shelves != null) {
                for (var shelf : shelves) {
                    feed.append("""
                              <entry>
                                <title>%s</title>
                                <id>urn:booklore:shelf:%d</id>
                                <updated>%s</updated>
                                <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                                <content type="text">Personal shelf collection</content>
                              </entry>
                            """.formatted(
                            escapeXml(shelf.getName()),
                            shelf.getId(),
                            now(),
                            escapeXml("/api/v1/opds/catalog?shelfId=" + shelf.getId())
                    ));
                }
            }
        }

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateMagicShelvesNavigation(HttpServletRequest request) {
        Long userId = getUserId();

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:navigation:magic-shelves</id>
                  <title>Magic Shelves</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds/magic-shelves" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now()));

        if (userId != null) {
            var magicShelves = magicShelfService.getUserShelvesForOpds(userId);

            if (magicShelves != null) {
                for (var shelf : magicShelves) {
                    feed.append("""
                              <entry>
                                <title>%s</title>
                                <id>urn:booklore:magic-shelf:%d</id>
                                <updated>%s</updated>
                                <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                                <content type="text">Smart, dynamic shelf collection</content>
                              </entry>
                            """.formatted(
                            escapeXml(shelf.getName()),
                            shelf.getId(),
                            now(),
                            escapeXml("/api/v1/opds/catalog?magicShelfId=" + shelf.getId())
                    ));
                }
            }
        }

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateAuthorsNavigation(HttpServletRequest request) {
        Long userId = getUserId();
        List<String> authors = opdsBookService.getDistinctAuthors(userId);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:navigation:authors</id>
                  <title>Authors</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds/authors" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now()));

        for (String author : authors) {
            feed.append("""
                      <entry>
                        <title>%s</title>
                        <id>urn:booklore:author:%s</id>
                        <updated>%s</updated>
                        <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                        <content type="text">Books by %s</content>
                      </entry>
                    """.formatted(
                    escapeXml(author),
                    escapeXml(author),
                    now(),
                    escapeXml("/api/v1/opds/catalog?author=" + java.net.URLEncoder.encode(author, java.nio.charset.StandardCharsets.UTF_8)),
                    escapeXml(author)
            ));
        }

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateSeriesNavigation(HttpServletRequest request) {
        Long userId = getUserId();
        List<String> seriesList = opdsBookService.getDistinctSeries(userId);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:navigation:series</id>
                  <title>Series</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds/series" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now()));

        for (String series : seriesList) {
            feed.append("""
                      <entry>
                        <title>%s</title>
                        <id>urn:booklore:series:%s</id>
                        <updated>%s</updated>
                        <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                        <content type="text">Books in the %s series</content>
                      </entry>
                    """.formatted(
                    escapeXml(series),
                    escapeXml(series),
                    now(),
                    escapeXml("/api/v1/opds/catalog?series=" + java.net.URLEncoder.encode(series, java.nio.charset.StandardCharsets.UTF_8)),
                    escapeXml(series)
            ));
        }

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateCatalogFeed(HttpServletRequest request) {
        Long libraryId = parseLongParam(request, "libraryId", null);
        Long shelfId = parseLongParam(request, "shelfId", null);
        Long magicShelfId = parseLongParam(request, "magicShelfId", null);
        String query = request.getParameter("q");
        String author = request.getParameter("author");
        String series = request.getParameter("series");
        int page = Math.max(1, parseLongParam(request, "page", 1L).intValue());
        int size = Math.min(parseLongParam(request, "size", (long) DEFAULT_PAGE_SIZE).intValue(), MAX_PAGE_SIZE);

        Long userId = getUserId();
        OpdsSortOrder sortOrder = getSortOrder();
        Page<Book> booksPage;

        if (magicShelfId != null) {
            booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, page - 1, size);
        } else if (author != null && !author.isBlank()) {
            booksPage = opdsBookService.getBooksByAuthorName(userId, author, page - 1, size);
        } else if (series != null && !series.isBlank()) {
            booksPage = opdsBookService.getBooksBySeriesName(userId, series, page - 1, size);
        } else {
            booksPage = opdsBookService.getBooksPage(userId, query, libraryId, shelfId, page - 1, size);
        }

        // Apply user's preferred sort order
        booksPage = opdsBookService.applySortOrder(booksPage, sortOrder);

        String feedTitle = determineFeedTitle(libraryId, shelfId, magicShelfId, author, series);
        String feedId = determineFeedId(libraryId, shelfId, magicShelfId, author, series);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/" xmlns:opds="http://opds-spec.org/2010/catalog" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
                  <id>%s</id>
                  <title>%s</title>
                  <updated>%s</updated>
                  <opensearch:totalResults>%d</opensearch:totalResults>
                  <opensearch:startIndex>%d</opensearch:startIndex>
                  <opensearch:itemsPerPage>%d</opensearch:itemsPerPage>
                  <link rel="self" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(
                feedId,
                escapeXml(feedTitle),
                now(),
                booksPage.getTotalElements(),
                ((page - 1) * size) + 1,
                size,
                escapeXml(buildCurrentUrl(request, page, size))
        ));

        appendPaginationLinks(feed, request, page, booksPage.getTotalPages(), size);

        booksPage.getContent().forEach(book -> appendBookEntry(feed, book));

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateRecentFeed(HttpServletRequest request) {
        Long userId = getUserId();
        OpdsSortOrder sortOrder = getSortOrder();
        int page = Math.max(1, parseLongParam(request, "page", 1L).intValue());
        int size = Math.min(parseLongParam(request, "size", (long) DEFAULT_PAGE_SIZE).intValue(), MAX_PAGE_SIZE);

        Page<Book> booksPage = opdsBookService.getRecentBooksPage(userId, page - 1, size);
        
        // Apply user's preferred sort order
        booksPage = opdsBookService.applySortOrder(booksPage, sortOrder);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/" xmlns:opds="http://opds-spec.org/2010/catalog" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
                  <id>urn:booklore:catalog:recent</id>
                  <title>Recently Added Books</title>
                  <updated>%s</updated>
                  <opensearch:totalResults>%d</opensearch:totalResults>
                  <opensearch:startIndex>%d</opensearch:startIndex>
                  <opensearch:itemsPerPage>%d</opensearch:itemsPerPage>
                  <link rel="self" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now(), booksPage.getTotalElements(), ((page - 1) * size) + 1, size, escapeXml(buildCurrentUrl(request, page, size))));

        appendPaginationLinks(feed, request, page, booksPage.getTotalPages(), size);

        booksPage.getContent().forEach(book -> appendBookEntry(feed, book));

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateSurpriseFeed(HttpServletRequest request) {
        Long userId = getUserId();
        int count = 25;
        List<Book> books = opdsBookService.getRandomBooks(userId, count);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/" xmlns:opds="http://opds-spec.org/2010/catalog" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
                  <id>urn:booklore:catalog:surprise</id>
                  <title>Surprise Me</title>
                  <updated>%s</updated>
                  <opensearch:totalResults>%d</opensearch:totalResults>
                  <opensearch:startIndex>1</opensearch:startIndex>
                  <opensearch:itemsPerPage>%d</opensearch:itemsPerPage>
                  <link rel="self" href="/api/v1/opds/surprise" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now(), books.size(), count));

        books.forEach(book -> appendBookEntry(feed, book));

        feed.append("</feed>");
        return feed.toString();
    }

    public String getOpenSearchDescription() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
                  <ShortName>Booklore</ShortName>
                  <Description>Search Booklore catalog</Description>
                  <Url type="application/atom+xml;profile=opds-catalog;kind=acquisition"
                       template="/api/v1/opds/catalog?q={searchTerms}"/>
                </OpenSearchDescription>
                """;
    }

    private void appendPaginationLinks(StringBuilder feed, HttpServletRequest request, int currentPage, int totalPages, int size) {
        if (totalPages > 0) {
            feed.append("  <link rel=\"first\" href=\"")
                    .append(escapeXml(buildPaginationUrl(request, 1, size)))
                    .append("\" type=\"application/atom+xml;profile=opds-catalog;kind=acquisition\"/>\n");
        }
        if (currentPage > 1) {
            feed.append("  <link rel=\"previous\" href=\"")
                    .append(escapeXml(buildPaginationUrl(request, currentPage - 1, size)))
                    .append("\" type=\"application/atom+xml;profile=opds-catalog;kind=acquisition\"/>\n");
        }
        if (currentPage < totalPages) {
            feed.append("  <link rel=\"next\" href=\"")
                    .append(escapeXml(buildPaginationUrl(request, currentPage + 1, size)))
                    .append("\" type=\"application/atom+xml;profile=opds-catalog;kind=acquisition\"/>\n");
        }
        if (totalPages > 0) {
            feed.append("  <link rel=\"last\" href=\"")
                    .append(escapeXml(buildPaginationUrl(request, totalPages, size)))
                    .append("\" type=\"application/atom+xml;profile=opds-catalog;kind=acquisition\"/>\n");
        }
    }

    private String buildPaginationUrl(HttpServletRequest request, int page, int size) {
        String url = request.getRequestURI();
        StringBuilder result = new StringBuilder(url).append("?");

        String queryString = request.getQueryString();
        if (queryString != null) {
            java.util.Arrays.stream(queryString.split("&"))
                    .filter(param -> !param.startsWith("page=") && !param.startsWith("size="))
                    .forEach(param -> result.append(param).append("&"));
        }

        result.append("page=").append(page).append("&size=").append(size);

        return result.toString();
    }

    private String buildCurrentUrl(HttpServletRequest request, int page, int size) {
        return buildPaginationUrl(request, page, size);
    }

    private void appendBookEntry(StringBuilder feed, Book book) {
        feed.append("""
                  <entry>
                    <title>%s</title>
                    <id>urn:booklore:book:%d</id>
                    <updated>%s</updated>
                """.formatted(
                escapeXml(book.getMetadata().getTitle()),
                book.getId(),
                book.getAddedOn() != null ? book.getAddedOn() : now()
        ));

        if (book.getMetadata().getAuthors() != null) {
            book.getMetadata().getAuthors().forEach(author ->
                    feed.append("    <author><name>").append(escapeXml(author)).append("</name></author>\n")
            );
        }

        appendMetadata(feed, book);
        appendLinks(feed, book);

        feed.append("  </entry>\n");
    }

    private void appendMetadata(StringBuilder feed, Book book) {
        var meta = book.getMetadata();
        if (meta == null) return;

        if (meta.getPublisher() != null) {
            feed.append("    <dc:publisher>").append(escapeXml(meta.getPublisher())).append("</dc:publisher>\n");
        }
        if (meta.getLanguage() != null) {
            feed.append("    <dc:language>").append(escapeXml(meta.getLanguage())).append("</dc:language>\n");
        }
        if (meta.getCategories() != null) {
            meta.getCategories().forEach(cat ->
                    feed.append("    <category term=\"").append(escapeXml(cat)).append("\"/>\n")
            );
        }
        if (meta.getDescription() != null) {
            feed.append("    <summary>").append(escapeXml(meta.getDescription())).append("</summary>\n");
        }
        if (meta.getIsbn10() != null) {
            feed.append("    <dc:identifier>urn:isbn:").append(escapeXml(meta.getIsbn10())).append("</dc:identifier>\n");
        }
        // Series metadata
        if (meta.getSeriesName() != null) {
            feed.append("    <meta property=\"belongs-to-collection\" id=\"series\">")
                    .append(escapeXml(meta.getSeriesName())).append("</meta>\n");
            if (meta.getSeriesNumber() != null) {
                feed.append("    <meta property=\"group-position\" refines=\"#series\">")
                        .append(meta.getSeriesNumber()).append("</meta>\n");
            }
        }
    }

    private void appendLinks(StringBuilder feed, Book book) {
        String mimeType = "application/" + fileMimeType(book);
        feed.append("    <link href=\"/api/v1/opds/")
                .append(book.getId()).append("/download\" rel=\"http://opds-spec.org/acquisition\" type=\"").append(mimeType).append("\"/>\n");

        if (book.getMetadata() != null && book.getMetadata().getCoverUpdatedOn() != null) {
            String coverUrl = "/api/v1/opds/" + book.getId() + "/cover?" + book.getMetadata().getCoverUpdatedOn();
            feed.append("    <link rel=\"http://opds-spec.org/image\" href=\"")
                    .append(escapeXml(coverUrl)).append("\" type=\"image/jpeg\"/>\n");
            feed.append("    <link rel=\"http://opds-spec.org/image/thumbnail\" href=\"")
                    .append(escapeXml(coverUrl)).append("\" type=\"image/jpeg\"/>\n");
        }
    }

    private String determineFeedTitle(Long libraryId, Long shelfId, Long magicShelfId, String author, String series) {
        if (magicShelfId != null) {
            return magicShelfBookService.getMagicShelfName(magicShelfId);
        }
        if (shelfId != null) {
            return opdsBookService.getShelfName(shelfId);
        }
        if (libraryId != null) {
            return opdsBookService.getLibraryName(libraryId);
        }
        if (author != null && !author.isBlank()) {
            return "Books by " + author;
        }
        if (series != null && !series.isBlank()) {
            return series + " series";
        }
        return "Booklore Catalog";
    }

    private String determineFeedId(Long libraryId, Long shelfId, Long magicShelfId, String author, String series) {
        if (magicShelfId != null) {
            return "urn:booklore:magic-shelf:" + magicShelfId;
        }
        if (shelfId != null) {
            return "urn:booklore:shelf:" + shelfId;
        }
        if (libraryId != null) {
            return "urn:booklore:library:" + libraryId;
        }
        if (author != null && !author.isBlank()) {
            return "urn:booklore:author:" + author;
        }
        if (series != null && !series.isBlank()) {
            return "urn:booklore:series:" + series;
        }
        return "urn:booklore:catalog";
    }

    private String now() {
        return DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now());
    }

    private String fileMimeType(Book book) {
        if (book == null || book.getBookType() == null) {
            return "octet-stream";
        }
        return switch (book.getBookType()) {
            case PDF -> "pdf";
            case EPUB -> "epub+zip";
            default -> "octet-stream";
        };
    }

    private String escapeXml(String input) {
        return input == null ? "" :
                input.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&apos;");
    }

    private Long parseLongParam(HttpServletRequest request, String name, Long defaultValue) {
        try {
            String v = request.getParameter(name);
            if (v == null || v.isBlank()) return defaultValue;
            return Long.parseLong(v);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Long getUserId() {
        OpdsUserDetails details = authenticationService.getOpdsUser();
        return details != null && details.getOpdsUserV2() != null
                ? details.getOpdsUserV2().getUserId()
                : null;
    }

    private OpdsSortOrder getSortOrder() {
        OpdsUserDetails details = authenticationService.getOpdsUser();
        return details != null && details.getOpdsUserV2() != null && details.getOpdsUserV2().getSortOrder() != null
                ? details.getOpdsUserV2().getSortOrder()
                : OpdsSortOrder.RECENT;
    }
}
