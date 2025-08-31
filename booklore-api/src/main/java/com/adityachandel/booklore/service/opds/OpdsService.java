package com.adityachandel.booklore.service.opds;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.BookQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpdsService {

    private final BookQueryService bookQueryService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    public String generateCatalogFeed(HttpServletRequest request) {
        List<Book> books = getAllowedBooks(null);
        String feedVersion = extractVersionFromAcceptHeader(request);
        return switch (feedVersion) {
            case "2.0" -> generateOpdsV2Feed(books);
            default -> generateOpdsV1Feed(books, request);
        };
    }

    public String generateSearchResults(HttpServletRequest request, String queryParam) {
        List<Book> books = getAllowedBooks(queryParam);
        String feedVersion = extractVersionFromAcceptHeader(request);
        return switch (feedVersion) {
            case "2.0" -> generateOpdsV2Feed(books);
            default -> generateOpdsV1Feed(books, request);
        };
    }

    private List<Book> getAllowedBooks(String queryParam) {
        OpdsUserDetails opdsUserDetails = authenticationService.getOpdsUser();
        OpdsUser opdsUser = opdsUserDetails.getOpdsUser();

        if (opdsUser != null) {
            return (queryParam != null)
                    ? bookQueryService.searchBooksByMetadata(queryParam)
                    : bookQueryService.getAllBooks(true);
        }

        OpdsUserV2 opdsUserV2 = opdsUserDetails.getOpdsUserV2();
        BookLoreUserEntity entity = userRepository.findById(opdsUserV2.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        if (!entity.getPermissions().isPermissionAccessOpds() && !entity.getPermissions().isPermissionAdmin()) {
            throw new AccessDeniedException("You are not allowed to access this resource");
        }

        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        boolean isAdmin = user.getPermissions().isAdmin();
        Set<Long> libraryIds = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());

        if (isAdmin) {
            return (queryParam != null)
                    ? bookQueryService.searchBooksByMetadata(queryParam)
                    : bookQueryService.getAllBooks(true);
        } else {
            return (queryParam != null)
                    ? bookQueryService.searchBooksByMetadataInLibraries(queryParam, libraryIds)
                    : bookQueryService.getAllBooksByLibraryIds(libraryIds, true);
        }
    }

    public String generateSearchDescription(HttpServletRequest request) {
        var feedVersion = extractVersionFromAcceptHeader(request);

        return switch (feedVersion) {
            case "2.0" -> generateOpdsV2SearchDescription();
            default -> generateOpdsV1SearchDescription();
        };
    }

    private String extractVersionFromAcceptHeader(HttpServletRequest request) {
        var acceptHeader = request.getHeader("Accept");
        return (acceptHeader != null && acceptHeader.contains("version=2.0")) ? "2.0" : "1.2";
    }

    private String generateOpdsV1SearchDescription() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
                    <LongName>Booklore catalog</LongName>
                    <Description>Search the Booklore ebook catalog.</Description>
                    <Url type="application/atom+xml;profile=opds-catalog" template="/api/v1/opds/search?q={searchTerms}"/>
                    <Language>en-us</Language>
                    <OutputEncoding>UTF-8</OutputEncoding>
                    <InputEncoding>UTF-8</InputEncoding>
                </OpenSearchDescription>
                """;
    }

    private String generateOpdsV1Feed(List<Book> books, HttpServletRequest request) {
        String version = extractVersionFromRequest(request); // v1 or v2
        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/">
                  <title>Booklore Catalog</title>
                  <id>urn:booklore:catalog</id>
                  <updated>%s</updated>
                  <link rel="search" type="application/opensearchdescription+xml" title="Booklore Search" href="/api/%s/opds/search.opds"/>
                """.formatted(now(), version));

        books.forEach(book -> appendBookEntryV1(feed, book, version));

        feed.append("</feed>");
        return feed.toString();
    }

    private void appendBookEntryV1(StringBuilder feed, Book book, String version) {
        feed.append("""
                <entry>
                  <title>%s</title>
                  <id>urn:booklore:book:%d</id>
                  <updated>%s</updated>
                """.formatted(escapeXml(book.getMetadata().getTitle()), book.getId(),
                book.getAddedOn() != null ? book.getAddedOn() : now()));

        book.getMetadata().getAuthors().forEach(author -> feed.append("""
                <author>
                  <name>%s</name>
                </author>
                """.formatted(escapeXml(author))));

        appendOptionalTags(feed, book, version);
        feed.append("  </entry>");
    }

    private void appendOptionalTags(StringBuilder feed, Book book, String version) {
        String basePath = "/api/" + version + "/opds/";

        if (book.getMetadata().getPublisher() != null) {
            feed.append("<dc:publisher>").append(escapeXml(book.getMetadata().getPublisher())).append("</dc:publisher>");
        }

        if (book.getMetadata().getLanguage() != null) {
            feed.append("<dc:language>").append(escapeXml(book.getMetadata().getLanguage())).append("</dc:language>");
        }

        if (book.getMetadata().getCategories() != null) {
            book.getMetadata().getCategories().forEach(category ->
                    feed.append("<dc:subject>").append(escapeXml(category)).append("</dc:subject>")
            );
        }

        if (book.getMetadata().getIsbn10() != null) {
            feed.append("<dc:identifier>").append(escapeXml("urn:isbn:" + book.getMetadata().getIsbn10())).append("</dc:identifier>");
        }

        if (book.getMetadata().getPublishedDate() != null) {
            feed.append("<published>").append(book.getMetadata().getPublishedDate()).append("</published>");
        }

        // download link
        feed.append("""
                <link href="%s%d/download" rel="http://opds-spec.org/acquisition" type="application/%s"/>
                """.formatted(basePath, book.getId(), fileMimeType(book)));

        // cover link
        if (book.getMetadata().getCoverUpdatedOn() != null) {
            String coverPath = basePath + book.getId() + "/cover?" + book.getMetadata().getCoverUpdatedOn();
            feed.append("""
                    <link rel="http://opds-spec.org/image" href="%s" type="image/jpeg"/>
                    <link rel="http://opds-spec.org/image/thumbnail" href="%s" type="image/jpeg"/>
                    """.formatted(escapeXml(coverPath), escapeXml(coverPath)));
        }

        if (book.getMetadata().getDescription() != null) {
            feed.append("<summary>").append(escapeXml(book.getMetadata().getDescription())).append("</summary>");
        }
    }

    private String generateOpdsV2Feed(List<Book> books) {
        // Placeholder for OPDS v2.0 feed implementation (similar structure as v1)
        return "OPDS v2.0 Feed is under construction";
    }

    private String generateOpdsV2SearchDescription() {
        // Placeholder for OPDS v2.0 feed implementation (similar structure as v1)
        return "OPDS v2.0 Feed is under construction";
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

    private String extractVersionFromRequest(HttpServletRequest request) {
        return (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/v2/opds")) ? "v2" : "v1";
    }
}
