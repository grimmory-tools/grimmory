package org.booklore.service.opds;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.Library;
import org.booklore.model.enums.OpdsSortOrder;
import org.booklore.service.MagicShelfService;
import org.booklore.util.ArchiveUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        String feed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:root</id>
                  <title>Booklore Catalog</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(now()) + """
                  <entry>
                    <title>All Books</title>
                    <id>urn:booklore:catalog:all</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                    <content type="text">Browse all available books</content>
                  </entry>
                """.formatted(now(), escapeXml("/api/v1/opds/catalog?page=1&size=" + DEFAULT_PAGE_SIZE)) +
                """
                          <entry>
                            <title>Recently Added</title>
                            <id>urn:booklore:catalog:recent</id>
                            <updated>%s</updated>
                            <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                            <content type="text">Recently added books</content>
                          </entry>
                        """.formatted(now(), escapeXml("/api/v1/opds/recent?page=1&size=" + DEFAULT_PAGE_SIZE)) +
                """
                          <entry>
                            <title>Libraries</title>
                            <id>urn:booklore:navigation:libraries</id>
                            <updated>%s</updated>
                            <link rel="subsection" href="/api/v1/opds/libraries" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                            <content type="text">Browse books by library</content>
                          </entry>
                        """.formatted(now()) +
                """
                          <entry>
                            <title>Shelves</title>
                            <id>urn:booklore:navigation:shelves</id>
                            <updated>%s</updated>
                            <link rel="subsection" href="/api/v1/opds/shelves" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                            <content type="text">Browse your personal shelves</content>
                          </entry>
                        """.formatted(now()) +
                """
                          <entry>
                            <title>Magic Shelves</title>
                            <id>urn:booklore:navigation:magic-shelves</id>
                            <updated>%s</updated>
                            <link rel="subsection" href="/api/v1/opds/magic-shelves" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                            <content type="text">Browse your smart, dynamic shelves</content>
                          </entry>
                        """.formatted(now()) +
                """
                          <entry>
                            <title>Authors</title>
                            <id>urn:booklore:navigation:authors</id>
                            <updated>%s</updated>
                            <link rel="subsection" href="/api/v1/opds/authors" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                            <content type="text">Browse books by author</content>
                          </entry>
                        """.formatted(now()) +
                """
                          <entry>
                            <title>Series</title>
                            <id>urn:booklore:navigation:series</id>
                            <updated>%s</updated>
                            <link rel="subsection" href="/api/v1/opds/series" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                            <content type="text">Browse books by series</content>
                          </entry>
                        """.formatted(now()) +
                """
                          <entry>
                            <title>Surprise Me</title>
                            <id>urn:booklore:catalog:surprise</id>
                            <updated>%s</updated>
                            <link rel="subsection" href="/api/v1/opds/surprise" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                            <content type="text">25 random books from the catalog</content>
                          </entry>
                        """.formatted(now()) +
                "</feed>";
        return feed;
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
                    escapeXml("/api/v1/opds/libraries/" + library.getId()),
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
        Set<Long> shelfIds = parseShelfIds(request);
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
            booksPage = opdsBookService.getBooksPage(userId, query, libraryId, shelfIds, page - 1, size);
        }

        // Apply user's preferred sort order
        booksPage = opdsBookService.applySortOrder(booksPage, sortOrder);

        String feedTitle = determineFeedTitle(libraryId, shelfIds, magicShelfId, author, series);
        String feedId = determineFeedId(libraryId, shelfIds, magicShelfId, author, series);

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
        // Add acquisition link for primary file
        if (book.getPrimaryFile() != null) {
            appendAcquisitionLink(feed, book.getId(), book.getPrimaryFile());
        }

        // Add acquisition links for alternative formats
        if (book.getAlternativeFormats() != null) {
            for (BookFile altFormat : book.getAlternativeFormats()) {
                appendAcquisitionLink(feed, book.getId(), altFormat);
            }
        }

        if (book.getMetadata() != null && book.getMetadata().getCoverUpdatedOn() != null) {
            String coverUrl = "/api/v1/opds/" + book.getId() + "/cover?" + book.getMetadata().getCoverUpdatedOn();
            feed.append("    <link rel=\"http://opds-spec.org/image\" href=\"")
                    .append(escapeXml(coverUrl)).append("\" type=\"image/jpeg\"/>\n");
            feed.append("    <link rel=\"http://opds-spec.org/image/thumbnail\" href=\"")
                    .append(escapeXml(coverUrl)).append("\" type=\"image/jpeg\"/>\n");
        }
    }

    private void appendAcquisitionLink(StringBuilder feed, Long bookId, BookFile bookFile) {
        if (bookFile == null || bookFile.getId() == null) return;

        String mimeType = fileMimeType(bookFile);
        feed.append("    <link href=\"/api/v1/opds/")
                .append(bookId)
                .append("/download?fileId=")
                .append(bookFile.getId())
                .append("\" rel=\"http://opds-spec.org/acquisition\" type=\"")
                .append(mimeType)
                .append("\"");

        // Add title attribute to help readers distinguish formats
        if (bookFile.getBookType() != null) {
            feed.append(" title=\"").append(bookFile.getBookType().name()).append("\"");
        }

        feed.append("/>\n");
    }

    private String determineFeedTitle(Long libraryId, Set<Long> shelfIds, Long magicShelfId, String author, String series) {
        if (magicShelfId != null) {
            return magicShelfBookService.getMagicShelfName(magicShelfId);
        }
        if (shelfIds != null && !shelfIds.isEmpty()) {
            if (shelfIds.size() == 1) {
                return opdsBookService.getShelfName(shelfIds.iterator().next());
            }
            return "Multiple Shelves";
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

    private String determineFeedId(Long libraryId, Set<Long> shelfIds, Long magicShelfId, String author, String series) {
        if (magicShelfId != null) {
            return "urn:booklore:magic-shelf:" + magicShelfId;
        }
        if (shelfIds != null && !shelfIds.isEmpty()) {
            if (shelfIds.size() == 1) {
                return "urn:booklore:shelf:" + shelfIds.iterator().next();
            }
            return "urn:booklore:shelves:" + String.join(",", shelfIds.stream().map(String::valueOf).sorted().toList());
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

    private boolean hasValidFilePath(BookFile bookFile) {
        return bookFile != null
                && bookFile.getFileName() != null
                && bookFile.getFilePath() != null;
    }

    private String fileMimeType(BookFile bookFile) {
        if (bookFile == null || bookFile.getBookType() == null) {
            return "application/octet-stream";
        }
        return switch (bookFile.getBookType()) {
            case PDF -> "application/pdf";
            case EPUB -> "application/epub+zip";
            case FB2 -> {
                if (hasValidFilePath(bookFile)) {
                    ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(new File(bookFile.getFilePath()));
                    if (type == ArchiveUtils.ArchiveType.ZIP) {
                        yield "application/zip";
                    }
                }
                yield "application/x-fictionbook+xml";
            }
            case MOBI -> "application/x-mobipocket-ebook";
            case AZW3 -> "application/vnd.amazon.ebook";
            case CBX -> {
                if (bookFile.getArchiveType() != null) {
                    if (bookFile.getArchiveType() == ArchiveUtils.ArchiveType.RAR) {
                        yield "application/vnd.comicbook-rar";
                    }
                    if (bookFile.getArchiveType() == ArchiveUtils.ArchiveType.ZIP) {
                        yield "application/vnd.comicbook+zip";
                    }
                    if (bookFile.getArchiveType() == ArchiveUtils.ArchiveType.SEVEN_ZIP) {
                        yield "application/x-7z-compressed";
                    }
                }

                if (hasValidFilePath(bookFile)) {
                    ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(new File(bookFile.getFilePath()));
                    // We only trust detection if it found something definite (not UNKNOWN)
                    if (type != ArchiveUtils.ArchiveType.UNKNOWN) {
                        yield switch (type) {
                            case RAR -> "application/vnd.comicbook-rar";
                            case ZIP -> "application/vnd.comicbook+zip";
                            case SEVEN_ZIP -> "application/x-7z-compressed";
                            default -> "application/vnd.comicbook+zip"; // Should not happen given the if check
                        };
                    }
                }

                String lower = bookFile.getFileName().toLowerCase();
                if (lower.endsWith(".cbr")) yield "application/vnd.comicbook-rar";
                if (lower.endsWith(".cbz")) yield "application/vnd.comicbook+zip";
                if (lower.endsWith(".cb7")) yield "application/x-7z-compressed";
                if (lower.endsWith(".cbt")) yield "application/x-tar";
                yield "application/vnd.comicbook+zip";
            }
            case AUDIOBOOK -> {
                String lower = bookFile.getFileName().toLowerCase();
                if (lower.endsWith(".m4b") || lower.endsWith(".m4a")) yield "audio/mp4";
                if (lower.endsWith(".mp3")) yield "audio/mpeg";
                if (lower.endsWith(".flac")) yield "audio/flac";
                if (lower.endsWith(".ogg") || lower.endsWith(".opus")) yield "audio/ogg";
                if (lower.endsWith(".aac")) yield "audio/aac";
                yield "audio/mp4";
            }
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

    private Set<Long> parseShelfIds(HttpServletRequest request) {
        String shelfIdParam = request.getParameter("shelfId");
        String shelfIdsParam = request.getParameter("shelfIds");

        Set<Long> shelfIds = new HashSet<>();

        // Support both single shelfId and comma-separated shelfIds
        if (shelfIdParam != null && !shelfIdParam.isBlank()) {
            try {
                shelfIds.add(Long.parseLong(shelfIdParam));
            } catch (NumberFormatException e) {
                log.warn("Invalid shelfId parameter: {}", shelfIdParam);
            }
        }

        if (shelfIdsParam != null && !shelfIdsParam.isBlank()) {
            for (String id : shelfIdsParam.split(",")) {
                try {
                    shelfIds.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid shelf ID in shelfIds parameter: {}", id);
                }
            }
        }

        return shelfIds.isEmpty() ? null : shelfIds;
    }

    public String generateLibraryNavigation(Long libraryId, HttpServletRequest request) {
        Long userId = getUserId();
        String libraryName = opdsBookService.getLibraryName(libraryId);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:library:%d</id>
                  <title>%s</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds/libraries/%d" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(libraryId, escapeXml(libraryName), now(), libraryId));

        // Authors navigation
        feed.append("""
                  <entry>
                    <title>Authors</title>
                    <id>urn:booklore:library:%d:authors</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                    <content type="text">Browse books by author in %s</content>
                  </entry>
                """.formatted(
                libraryId,
                now(),
                escapeXml("/api/v1/opds/libraries/" + libraryId + "/authors"),
                escapeXml(libraryName)
        ));

        // Series navigation
        feed.append("""
                  <entry>
                    <title>Series</title>
                    <id>urn:booklore:library:%d:series</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                    <content type="text">Browse books by series in %s</content>
                  </entry>
                """.formatted(
                libraryId,
                now(),
                escapeXml("/api/v1/opds/libraries/" + libraryId + "/series"),
                escapeXml(libraryName)
        ));

        // Recently Added acquisition
        feed.append("""
                  <entry>
                    <title>Recently Added</title>
                    <id>urn:booklore:library:%d:recent</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                    <content type="text">Recently added books in %s</content>
                  </entry>
                """.formatted(
                libraryId,
                now(),
                escapeXml("/api/v1/opds/libraries/" + libraryId + "/recent?page=1&size=50"),
                escapeXml(libraryName)
        ));

        // All Books acquisition
        feed.append("""
                  <entry>
                    <title>All Books</title>
                    <id>urn:booklore:library:%d:catalog</id>
                    <updated>%s</updated>
                    <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                    <content type="text">All books in %s</content>
                  </entry>
                """.formatted(
                libraryId,
                now(),
                escapeXml("/api/v1/opds/libraries/" + libraryId + "/catalog?page=1&size=50"),
                escapeXml(libraryName)
        ));

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateLibraryAuthorsNavigation(Long libraryId, HttpServletRequest request) {
        Long userId = getUserId();
        List<String> authors = opdsBookService.getDistinctAuthors(userId, libraryId);
        String libraryName = opdsBookService.getLibraryName(libraryId);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:library:%d:authors</id>
                  <title>Authors in %s</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds/libraries/%d/authors" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="up" href="/api/v1/opds/libraries/%d" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(libraryId, escapeXml(libraryName), now(), libraryId, libraryId));

        for (String author : authors) {
            feed.append("""
                      <entry>
                        <title>%s</title>
                        <id>urn:booklore:library:%d:author:%s</id>
                        <updated>%s</updated>
                        <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                        <content type="text">Books by %s in %s</content>
                      </entry>
                    """.formatted(
                    escapeXml(author),
                    libraryId,
                    escapeXml(author),
                    now(),
                    escapeXml("/api/v1/opds/catalog?libraryId=" + libraryId + "&author=" + java.net.URLEncoder.encode(author, java.nio.charset.StandardCharsets.UTF_8)),
                    escapeXml(author),
                    escapeXml(libraryName)
            ));
        }

        feed.append("</feed>");
        return feed.toString();
    }

    public String generateLibrarySeriesNavigation(Long libraryId, HttpServletRequest request) {
        Long userId = getUserId();
        List<String> seriesList = opdsBookService.getDistinctSeries(userId, libraryId);
        String libraryName = opdsBookService.getLibraryName(libraryId);

        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                  <id>urn:booklore:library:%d:series</id>
                  <title>Series in %s</title>
                  <updated>%s</updated>
                  <link rel="self" href="/api/v1/opds/libraries/%d/series" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="start" href="/api/v1/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="up" href="/api/v1/opds/libraries/%d" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                  <link rel="search" type="application/opensearchdescription+xml" title="Search" href="/api/v1/opds/search.opds"/>
                """.formatted(libraryId, escapeXml(libraryName), now(), libraryId, libraryId));

        for (String series : seriesList) {
            feed.append("""
                      <entry>
                        <title>%s</title>
                        <id>urn:booklore:library:%d:series:%s</id>
                        <updated>%s</updated>
                        <link rel="subsection" href="%s" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
                        <content type="text">Books in the %s series in %s</content>
                      </entry>
                    """.formatted(
                    escapeXml(series),
                    libraryId,
                    escapeXml(series),
                    now(),
                    escapeXml("/api/v1/opds/catalog?libraryId=" + libraryId + "&series=" + java.net.URLEncoder.encode(series, java.nio.charset.StandardCharsets.UTF_8)),
                    escapeXml(series),
                    escapeXml(libraryName)
            ));
        }

        feed.append("</feed>");
        return feed.toString();
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
