package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class EpubMetadataExtractor implements FileMetadataExtractor {

    @Override
    public byte[] extractCover(File epubFile) {
        try {
            Book epub = new EpubReader().readEpub(new FileInputStream(epubFile));
            io.documentnode.epub4j.domain.Resource coverImage = epub.getCoverImage();

            if (coverImage == null) {
                for (io.documentnode.epub4j.domain.Resource res : epub.getResources().getAll()) {
                    String id = res.getId();
                    String href = res.getHref();
                    if ((id != null && id.toLowerCase().contains("cover")) ||
                            (href != null && href.toLowerCase().contains("cover"))) {
                        if (res.getMediaType() != null && res.getMediaType().getName().startsWith("image")) {
                            coverImage = res;
                            break;
                        }
                    }
                }
            }

            return (coverImage != null) ? coverImage.getData() : null;
        } catch (Exception e) {
            log.warn("Failed to extract cover from EPUB: {}", epubFile.getName(), e);
            return null;
        }
    }

    public BookMetadata extractMetadata(File epubFile) {
        try (ZipFile zip = new ZipFile(epubFile)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = dbf.newDocumentBuilder();

            FileHeader containerHdr = zip.getFileHeader("META-INF/container.xml");
            if (containerHdr == null) return null;

            try (InputStream cis = zip.getInputStream(containerHdr)) {
                Document containerDoc = builder.parse(cis);
                NodeList roots = containerDoc.getElementsByTagName("rootfile");
                if (roots.getLength() == 0) return null;

                String opfPath = ((Element) roots.item(0)).getAttribute("full-path");
                if (StringUtils.isBlank(opfPath)) return null;

                FileHeader opfHdr = zip.getFileHeader(opfPath);
                if (opfHdr == null) return null;

                try (InputStream in = zip.getInputStream(opfHdr)) {
                    Document doc = builder.parse(in);
                    Element metadata = (Element) doc.getElementsByTagNameNS("*", "metadata").item(0);
                    if (metadata == null) return null;

                    BookMetadata.BookMetadataBuilder builderMeta = BookMetadata.builder();
                    Set<String> authors = new HashSet<>();
                    Set<String> categories = new HashSet<>();

                    boolean seriesFound = false;
                    boolean seriesIndexFound = false;

                    NodeList children = metadata.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        if (!(children.item(i) instanceof Element el)) continue;

                        String tag = el.getLocalName();
                        String text = el.getTextContent().trim();

                        switch (tag) {
                            case "title" -> builderMeta.title(text);
                            case "description" -> builderMeta.description(text);
                            case "publisher" -> builderMeta.publisher(text);
                            case "language" -> builderMeta.language(text);
                            case "creator" -> authors.add(text);
                            case "subject" -> categories.add(text);
                            case "identifier" -> {
                                String scheme = el.getAttributeNS("http://www.idpf.org/2007/opf", "scheme").toUpperCase();
                                String value = text.toLowerCase().startsWith("isbn:") ? text.substring(5) : text;

                                if (!scheme.isEmpty()) {
                                    switch (scheme) {
                                        case "ISBN" -> {
                                            if (value.length() == 13) builderMeta.isbn13(value);
                                            else if (value.length() == 10) builderMeta.isbn10(value);
                                        }
                                        case "GOODREADS" -> builderMeta.goodreadsId(value);
                                        case "COMICVINE" -> builderMeta.comicvineId(value);
                                        case "GOOGLE" -> builderMeta.googleId(value);
                                        case "AMAZON" -> builderMeta.asin(value);
                                        case "HARDCOVER" -> builderMeta.hardcoverId(value);
                                    }
                                } else {
                                    if (text.toLowerCase().startsWith("isbn:")) {
                                        if (value.length() == 13) builderMeta.isbn13(value);
                                        else if (value.length() == 10) builderMeta.isbn10(value);
                                    }
                                }
                           }
                            case "date" -> {
                                LocalDate parsed = parseDate(text);
                                if (parsed != null) builderMeta.publishedDate(parsed);
                            }
                            case "meta" -> {
                                String name = el.getAttribute("name").trim().toLowerCase();
                                String prop = el.getAttribute("property").trim().toLowerCase();
                                String content = el.hasAttribute("content") ? el.getAttribute("content").trim() : text;
                                if (StringUtils.isBlank(content)) continue;

                                if (!seriesFound && ("booklore:series".equals(prop) || "calibre:series".equals(name) || "calibre:series".equals(prop) || "belongs-to-collection".equals(prop))) {
                                    builderMeta.seriesName(content);
                                    seriesFound = true;
                                }

                                if (!seriesIndexFound && ("booklore:series_index".equals(prop) || "calibre:series_index".equals(name) || "calibre:series_index".equals(prop) || "group-position".equals(prop))) {
                                    try {
                                        builderMeta.seriesNumber(Float.parseFloat(content));
                                        seriesIndexFound = true;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }

                                if ("calibre:pages".equals(name) || "pagecount".equals(name) || "schema:pagecount".equals(prop) || "media:pagecount".equals(prop) || "booklore:page_count".equals(prop)) {
                                    safeParseInt(content, builderMeta::pageCount);
                                } else if (name.equals("calibre:user_metadata:#pagecount")) {
                                    try {
                                        JSONObject jsonroot = new JSONObject(content);
                                        Object value = jsonroot.opt("#value#");
                                        safeParseInt(String.valueOf(value), builderMeta::pageCount);
                                    } catch (JSONException ignored) {
                                    }
                                } else if (prop.equals("calibre:user_metadata")) {
                                    try {
                                        JSONObject jsonroot = new JSONObject(content);
                                        JSONObject pages = jsonroot.getJSONObject("#pagecount");
                                        Object value = pages.opt("#value#");
                                        safeParseInt(String.valueOf(value), builderMeta::pageCount);
                                    } catch (JSONException ignored) {
                                    }
                                }

                                if ("calibre:rating".equals(name) || "booklore:personal_rating".equals(prop)) {
                                    safeParseDouble(content, builderMeta::personalRating);
                                }

                                switch (prop) {
                                    case "booklore:asin" -> builderMeta.asin(content);
                                    case "booklore:goodreads_id" -> builderMeta.goodreadsId(content);
                                    case "booklore:comicvine_id" -> builderMeta.comicvineId(content);
                                    case "booklore:hardcover_id" -> builderMeta.hardcoverId(content);
                                    case "booklore:google_books_id" -> builderMeta.googleId(content);
                                    case "booklore:page_count" -> safeParseInt(content, builderMeta::pageCount);
                                }
                            }
                        }
                    }

                    if (builderMeta.build().getPublishedDate() == null) {
                        for (int i = 0; i < children.getLength(); i++) {
                            if (!(children.item(i) instanceof Element el)) continue;
                            if (!"meta".equals(el.getLocalName())) continue;

                            String prop = el.getAttribute("property").trim().toLowerCase();
                            String content = el.hasAttribute("content") ? el.getAttribute("content").trim() : el.getTextContent().trim();
                            if ("dcterms:modified".equals(prop)) {
                                LocalDate parsed = parseDate(content);
                                if (parsed != null) {
                                    builderMeta.publishedDate(parsed);
                                    break;
                                }
                            }
                        }
                    }

                    builderMeta.authors(authors);
                    builderMeta.categories(categories);

                    BookMetadata extractedMetadata = builderMeta.build();
                    // Fallback to filename if no title found in EPUB metadata
                    if (StringUtils.isBlank(extractedMetadata.getTitle())) {
                        builderMeta.title(FilenameUtils.getBaseName(epubFile.getName()));
                        extractedMetadata = builderMeta.build();
                    }

                    return extractedMetadata;
                }
            }

        } catch (Exception e) {
            log.error("Failed to read metadata from EPUB file {}: {}", epubFile.getName(), e.getMessage(), e);
            return null;
        }
    }


    private void safeParseInt(String value, java.util.function.IntConsumer setter) {
        try {
            setter.accept(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void safeParseDouble(String value, java.util.function.DoubleConsumer setter) {
        try {
            setter.accept(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) return null;

        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (Exception ignored) {
        }

        log.warn("Failed to parse date from string: {}", value);
        return null;
    }
}
