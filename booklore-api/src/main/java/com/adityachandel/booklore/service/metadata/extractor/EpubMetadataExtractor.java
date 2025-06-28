package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class EpubMetadataExtractor implements FileMetadataExtractor {

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

                                switch (scheme) {
                                    case "ISBN" -> {
                                        if (value.length() == 13) builderMeta.isbn13(value);
                                        else if (value.length() == 10) builderMeta.isbn10(value);
                                    }
                                    case "GOODREADS" -> builderMeta.goodreadsId(value);
                                    case "GOOGLE" -> builderMeta.googleId(value);
                                    case "AMAZON" -> builderMeta.asin(value);
                                    case "HARDCOVER" -> builderMeta.hardcoverId(value);
                                }
                            }
                            case "date" -> {
                                try {
                                    builderMeta.publishedDate(LocalDate.parse(text));
                                } catch (Exception e) {
                                    log.warn("Invalid date format in OPF: {}", text);
                                }
                            }
                            case "meta" -> {
                                String name = el.getAttribute("name").trim().toLowerCase();
                                String prop = el.getAttribute("property").trim().toLowerCase();
                                String content = el.hasAttribute("content") ? el.getAttribute("content").trim() : text;
                                if (StringUtils.isBlank(content)) continue;

                                if (!seriesFound && (prop.equals("booklore:series") || name.equals("calibre:series") || prop.equals("calibre:series") || prop.equals("belongs-to-collection"))) {
                                    builderMeta.seriesName(content);
                                    seriesFound = true;
                                }

                                if (!seriesIndexFound && (prop.equals("booklore:series_index") || name.equals("calibre:series_index") || prop.equals("calibre:series_index") || prop.equals("group-position"))) {
                                    try {
                                        builderMeta.seriesNumber(Float.parseFloat(content));
                                        seriesIndexFound = true;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }

                                if (name.equals("calibre:pages") || name.equals("pagecount") || prop.equals("schema:pagecount") || prop.equals("media:pagecount") || prop.equals("booklore:page_count")) {
                                    try {
                                        builderMeta.pageCount(Integer.parseInt(content));
                                    } catch (NumberFormatException ignored) {
                                    }
                                }

                                if (name.equals("calibre:rating") || prop.equals("booklore:personal_rating")) {
                                    safeParseDouble(content, builderMeta::personalRating);
                                }

                                switch (prop) {
                                    case "booklore:asin" -> builderMeta.asin(content);
                                    case "booklore:goodreads_id" -> builderMeta.goodreadsId(content);
                                    case "booklore:hardcover_id" -> builderMeta.hardcoverId(content);
                                    case "booklore:google_books_id" -> builderMeta.googleId(content);
                                    case "booklore:page_count" -> safeParseInt(content, builderMeta::pageCount);
                                }
                            }
                        }
                    }

                    builderMeta.authors(authors);
                    builderMeta.categories(categories);
                    return builderMeta.build();
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
}