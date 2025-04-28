package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpdsService {

    private final BookRepository bookRepository;

    public String generateCatalogFeed(HttpServletRequest request) {
        var books = bookRepository.findAll();
        var feedVersion = extractVersionFromAcceptHeader(request);

        return switch (feedVersion) {
            case "2.0" -> generateOpdsV2Feed(books);
            default -> generateOpdsV1Feed(books);
        };
    }

    private String extractVersionFromAcceptHeader(HttpServletRequest request) {
        var acceptHeader = request.getHeader("Accept");
        return (acceptHeader != null && acceptHeader.contains("version=2.0")) ? "2.0" : "1.2";
    }

    private String generateOpdsV1Feed(List<BookEntity> books) {
        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/">
                  <title>Booklore Catalog</title>
                  <id>urn:booklore:catalog</id>
                  <updated>%s</updated>
                """.formatted(now()));

        books.forEach(book -> appendBookEntryV1(feed, book));

        feed.append("</feed>");
        return feed.toString();
    }

    private void appendBookEntryV1(StringBuilder feed, BookEntity book) {
        feed.append("""
                <entry>
                  <title>%s</title>
                  <id>urn:booklore:book:%d</id>
                  <updated>%s</updated>
                """.formatted(escapeXml(book.getMetadata().getTitle()), book.getId(), book.getAddedOn() != null ? book.getAddedOn() : now()));

        book.getMetadata().getAuthors().forEach(author -> feed.append("""
                <author>
                  <name>%s</name>
                </author>
                """.formatted(escapeXml(author.getName()))));

        appendOptionalTags(feed, book);
        feed.append("  </entry>");
    }

    private void appendOptionalTags(StringBuilder feed, BookEntity book) {
        if (book.getMetadata().getPublisher() != null) {
            feed.append("<dc:publisher>").append(escapeXml(book.getMetadata().getPublisher())).append("</dc:publisher>");
        }

        if (book.getMetadata().getLanguage() != null) {
            feed.append("<dc:language>").append(escapeXml(book.getMetadata().getLanguage())).append("</dc:language>");
        }

        if (book.getMetadata().getCategories() != null) {
            book.getMetadata().getCategories().forEach(category ->
                    feed.append("<dc:subject>").append(escapeXml(category.getName())).append("</dc:subject>")
            );
        }

        if (book.getMetadata().getIsbn10() != null) {
            feed.append("<dc:identifier>").append(escapeXml("urn:isbn:" + book.getMetadata().getIsbn10())).append("</dc:identifier>");
        }

        if (book.getMetadata().getPublishedDate() != null) {
            feed.append("<published>").append(book.getMetadata().getPublishedDate()).append("</published>");
        }

        feed.append("""
                <link href="/api/v1/opds/%d/download" rel="http://opds-spec.org/acquisition" type="application/%s"/>
                """.formatted(book.getId(), fileMimeType(book)));

        if (book.getMetadata().getCoverUpdatedOn() != null) {
            String coverPath = "/api/v1/opds/" + book.getId() + "/cover.jpg?" + book.getMetadata().getCoverUpdatedOn();
            feed.append("""
                    <link rel="http://opds-spec.org/image" href="%s" type="image/jpeg"/>
                    <link rel="http://opds-spec.org/image/thumbnail" href="%s" type="image/jpeg"/>
                    """.formatted(escapeXml(coverPath), escapeXml(coverPath)));
        }

        if (book.getMetadata().getDescription() != null) {
            feed.append("<summary>").append(escapeXml(book.getMetadata().getDescription())).append("</summary>");
        }
    }

    private String generateOpdsV2Feed(List<BookEntity> books) {
        // Placeholder for OPDS v2.0 feed implementation (similar structure as v1)
        return "OPDS v2.0 Feed is under construction";
    }

    private String now() {
        return DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now());
    }

    private String fileMimeType(BookEntity book) {
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
}