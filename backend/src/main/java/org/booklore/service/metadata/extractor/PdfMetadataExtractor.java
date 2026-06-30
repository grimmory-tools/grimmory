package org.booklore.service.metadata.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.XmpMetadataParser;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.booklore.model.dto.BookMetadata;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import static java.time.temporal.ChronoField.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.DoubleConsumer;

@Component
@Slf4j
public class PdfMetadataExtractor implements FileMetadataExtractor {


    private static final Pattern COMMA_AMPERSAND_PATTERN = Pattern.compile("[,&]");
    private static final Pattern ISBN_CLEANUP_PATTERN = Pattern.compile("[^0-9Xx]");
    private static final Pattern SERIES_INDEX_PATTERN = Pattern.compile("<series_index>([^<]+)</series_index>");

    private static String toPascalCase(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private static Set<String> splitSemicolon(String value) {
        return Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public byte[] extractCover(File file) {
        try (PdfDocument doc = PdfDocument.open(file.toPath())) {
            return doc.renderPageToBytes(0, 150, "jpeg");
        } catch (Exception e) {
            log.warn("Failed to extract cover from PDF: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    @Override
    public BookMetadata extractMetadata(File file) {
        if (!file.exists() || !file.isFile()) {
            log.warn("File does not exist or is not a file: {}", file.getPath());
            return BookMetadata.builder().build();
        }

        try (PdfDocument doc = PdfDocument.open(file.toPath())) {
            XmpMetadata xmp = XmpMetadataParser.parseFrom(doc);

            BookMetadata.BookMetadataBuilder metadataBuilder = BookMetadata.builder()
                    .title(xmp.title().filter(StringUtils::isNotBlank)
                            .or(() -> doc.metadata(MetadataTag.TITLE).filter(StringUtils::isNotBlank))
                            .orElse(FilenameUtils.getBaseName(file.getName())))
                    .description(xmp.description().filter(StringUtils::isNotBlank)
                            .or(() -> doc.metadata(MetadataTag.SUBJECT).filter(StringUtils::isNotBlank))
                            .orElse(null))
                    .publisher(xmp.publisher().filter(StringUtils::isNotBlank)
                            .or(() -> doc.metadata("EBX_PUBLISHER").filter(StringUtils::isNotBlank))
                            .orElse(null))
                    .language(xmp.language().filter(StringUtils::isNotBlank)
                            .or(() -> doc.metadata("Language").filter(StringUtils::isNotBlank))
                            .orElse(null))
                    .pageCount(doc.pageCount());

            // Authors
            if (!xmp.creators().isEmpty()) {
                metadataBuilder.authors(xmp.creators());
            } else {
                doc.metadata(MetadataTag.AUTHOR).ifPresent(a -> {
                    List<String> authors = Arrays.stream(COMMA_AMPERSAND_PATTERN.split(a))
                            .map(String::trim)
                            .filter(StringUtils::isNotBlank)
                            .toList();
                    if (!authors.isEmpty()) metadataBuilder.authors(authors);
                });
            }

            // Date
            xmp.date().map(Object::toString).map(this::parsePdfDate)
                    .or(() -> doc.metadata(MetadataTag.CREATION_DATE).map(this::parsePdfDate))
                    .ifPresent(metadataBuilder::publishedDate);

            // Moods and Tags (List/Bag with semicolon fallback)
            Set<String> moodsSet = new LinkedHashSet<>(findCustomListField(xmp, "moods"));
            if (moodsSet.isEmpty()) {
                findCustomField(xmp, "moods").map(PdfMetadataExtractor::splitSemicolon).ifPresent(moodsSet::addAll);
            }
            if (!moodsSet.isEmpty()) metadataBuilder.moods(moodsSet);

            Set<String> tagsSet = new LinkedHashSet<>(findCustomListField(xmp, "tags"));
            if (tagsSet.isEmpty()) {
                findCustomField(xmp, "tags").map(PdfMetadataExtractor::splitSemicolon).ifPresent(tagsSet::addAll);
            }
            if (!tagsSet.isEmpty()) metadataBuilder.tags(tagsSet);

            // Categories (Keywords / Subjects), filtering out moods and tags
            Set<String> categories = new HashSet<>(xmp.subjects());
            if (categories.isEmpty()) {
                doc.metadata(MetadataTag.KEYWORDS).ifPresent(k -> {
                    String[] parts = k.contains(";") ? k.split(";") : k.split(",");
                    Arrays.stream(parts).map(String::trim).filter(StringUtils::isNotBlank).forEach(categories::add);
                });
            }
            categories.removeAll(moodsSet);
            categories.removeAll(tagsSet);
            if (!categories.isEmpty()) metadataBuilder.categories(categories);

            // Calibre & Series
            xmp.calibreSeries().or(() -> doc.metadata("Series")).ifPresent(metadataBuilder::seriesName);
            Optional<Float> seriesNumber = xmp.calibreSeriesIndex().map(Double::floatValue)
                    .or(() -> doc.metadata("SeriesNumber").flatMap(val -> {
                        try { return Optional.of(Float.parseFloat(val)); } catch (Exception _) { return Optional.empty(); }
                    }));

            // Calibre fallback for un-prefixed series_index
            if (seriesNumber.isEmpty()) {
                String xmpStr = doc.xmpMetadataString();
                Matcher siMatcher = SERIES_INDEX_PATTERN.matcher(xmpStr);
                if (siMatcher.find()) {
                    try { seriesNumber = Optional.of(Float.parseFloat(siMatcher.group(1).trim())); } catch (Exception _) {}
                }
            }

            // BL Custom Fields
            findCustomField(xmp, "seriesName").ifPresent(metadataBuilder::seriesName);
            Optional<Float> bookloreSeriesNumber = findCustomField(xmp, "seriesNumber").flatMap(val -> {
                try { return Optional.of(Float.parseFloat(val)); } catch (Exception _) { return Optional.empty(); }
            });
            if (bookloreSeriesNumber.isPresent()) seriesNumber = bookloreSeriesNumber;

            seriesNumber.ifPresent(metadataBuilder::seriesNumber);

            findCustomField(xmp, "seriesTotal").ifPresent(val -> {
                try { metadataBuilder.seriesTotal(Integer.parseInt(val)); } catch (Exception _) {}
            });
            findCustomField(xmp, "subtitle").or(() -> doc.metadata("Subtitle")).ifPresent(metadataBuilder::subtitle);

            // Identifiers (DocInfo first)
            doc.metadata("ISBN").ifPresent(val -> mapIsbn(val, metadataBuilder));

            // Identifiers (Booklore / Custom XMP)
            findCustomField(xmp, "isbn13").ifPresent(val -> metadataBuilder.isbn13(cleanIsbn(val)));
            findCustomField(xmp, "isbn10").ifPresent(val -> metadataBuilder.isbn10(cleanIsbn(val)));
            findCustomField(xmp, "googleId").ifPresent(metadataBuilder::googleId);
            findCustomField(xmp, "goodreadsId").ifPresent(metadataBuilder::goodreadsId);
            findCustomField(xmp, "amazonId").ifPresent(metadataBuilder::asin);
            findCustomField(xmp, "asin").ifPresent(metadataBuilder::asin);
            findCustomField(xmp, "comicvineId").ifPresent(metadataBuilder::comicvineId);
            findCustomField(xmp, "ranobedbId").ifPresent(metadataBuilder::ranobedbId);
            findCustomField(xmp, "lubimyczytacId").ifPresent(metadataBuilder::lubimyczytacId);
            findCustomField(xmp, "hardcoverId").ifPresent(metadataBuilder::hardcoverId);
            findCustomField(xmp, "hardcoverBookId").ifPresent(metadataBuilder::hardcoverBookId);

            // Identifiers (xmp:Identifier and their derived fields)
            xmp.isbns().forEach(val -> mapIsbn(val, metadataBuilder));
            xmp.xmpIdentifier("isbn").ifPresent(val -> mapIsbn(val, metadataBuilder));
            xmp.xmpIdentifier("isbn13").ifPresent(val -> metadataBuilder.isbn13(cleanIsbn(val)));
            xmp.xmpIdentifier("isbn10").ifPresent(val -> metadataBuilder.isbn10(cleanIsbn(val)));
            xmp.xmpIdentifier("google").ifPresent(metadataBuilder::googleId);
            xmp.xmpIdentifier("amazon").ifPresent(metadataBuilder::asin);
            xmp.xmpIdentifier("asin").ifPresent(metadataBuilder::asin);
            xmp.xmpIdentifier("goodreads").ifPresent(metadataBuilder::goodreadsId);
            xmp.xmpIdentifier("comicvine").ifPresent(metadataBuilder::comicvineId);
            xmp.xmpIdentifier("ranobedb").ifPresent(metadataBuilder::ranobedbId);
            xmp.xmpIdentifier("lubimyczytac").ifPresent(metadataBuilder::lubimyczytacId);
            xmp.xmpIdentifier("hardcover").ifPresent(metadataBuilder::hardcoverId);
            xmp.xmpIdentifier("hardcover_book_id").ifPresent(metadataBuilder::hardcoverBookId);

            // Ratings
            mapRating(xmp, "rating", "Rating", metadataBuilder::rating);
            mapRating(xmp, "amazonRating", "AmazonRating", metadataBuilder::amazonRating);
            mapRating(xmp, "goodreadsRating", "GoodreadsRating", metadataBuilder::goodreadsRating);
            mapRating(xmp, "hardcoverRating", "HardcoverRating", metadataBuilder::hardcoverRating);
            mapRating(xmp, "lubimyczytacRating", "LubimyczytacRating", metadataBuilder::lubimyczytacRating);
            mapRating(xmp, "ranobedbRating", "RanobedbRating", metadataBuilder::ranobedbRating);

            return metadataBuilder.build();
        } catch (Exception e) {
            log.error("Failed to load PDF file: {}", file.getPath(), e);
            return BookMetadata.builder().build();
        }
    }

    private void mapIsbn(String value, BookMetadata.BookMetadataBuilder builder) {
        String cleaned = cleanIsbn(value);
        switch (cleaned.length()) {
            case 13 -> builder.isbn13(cleaned);
            case 10 -> builder.isbn10(cleaned);
            default -> log.debug("Unrecognized ISBN format (length={}): {}", cleaned.length(), cleaned);
        }
    }


    private Optional<String> findCustomField(XmpMetadata xmp, String name) {
        return xmp.findField(name)
                .or(() -> xmp.findField(toPascalCase(name)));
    }

    private List<String> findCustomListField(XmpMetadata xmp, String name) {
        List<String> values = xmp.findListField(name);
        return values.isEmpty() ? xmp.findListField(toPascalCase(name)) : values;
    }

    private static String cleanIsbn(String value) {
        return ISBN_CLEANUP_PATTERN.matcher(value).replaceAll("");
    }

    private static final DateTimeFormatter PDF_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .optionalStart().appendLiteral("D:").optionalEnd()
            .appendValue(YEAR, 4)
            .optionalStart().appendValue(MONTH_OF_YEAR, 2)
            .optionalStart().appendValue(DAY_OF_MONTH, 2)
            .optionalStart().appendValue(HOUR_OF_DAY, 2)
            .optionalStart().appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart().appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart().appendOffset("+HHmm", "Z").optionalEnd()
            .optionalStart().appendOffset("+HH:mm", "Z").optionalEnd()
            .optionalEnd().optionalEnd().optionalEnd().optionalEnd().optionalEnd()
            .toFormatter();

    private LocalDate parsePdfDate(String pdfDate) {
        if (pdfDate == null || pdfDate.isBlank()) return null;
        String cleaned = pdfDate.replace("'", "").trim();
        try {
            return switch (PDF_DATE_FORMATTER.parseBest(
                    cleaned,
                    LocalDate::from,
                    LocalDateTime::from,
                    OffsetDateTime::from,
                    YearMonth::from,
                    Year::from)) {
                case LocalDate ld          -> ld;
                case LocalDateTime ldt     -> ldt.toLocalDate();
                case OffsetDateTime odt    -> odt.toLocalDate();
                case YearMonth ym          -> ym.atDay(1);
                case Year y                -> y.atDay(1);
                default                    -> null;
            };
        } catch (Exception _) {
            // Try ISO format as fallback
            try {
                return OffsetDateTime.parse(cleaned).toLocalDate();
            } catch (Exception _) {
                try {
                    return LocalDateTime.parse(cleaned).toLocalDate();
                } catch (Exception _) {
                    try {
                        return LocalDate.parse(cleaned);
                    } catch (Exception _) {
                        return null;
                    }
                }
            }
        }
    }

    private void mapRating(XmpMetadata xmp, String name, String fallbackName, DoubleConsumer setter) {
        Optional<String> val = findCustomField(xmp, name);
        if (val.isEmpty()) val = findCustomField(xmp, fallbackName);
        val.ifPresent(v -> {
            try {
                setter.accept(Double.parseDouble(v));
            } catch (Exception _) {
                // Ignore invalid ratings
            }
        });
    }
}
