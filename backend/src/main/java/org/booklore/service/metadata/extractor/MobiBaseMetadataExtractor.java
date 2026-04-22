package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public abstract class MobiBaseMetadataExtractor implements FileMetadataExtractor {

    // EXTH record types
    protected static final int EXTH_AUTHOR = 100;
    protected static final int EXTH_PUBLISHER = 101;
    protected static final int EXTH_DESCRIPTION = 103;
    protected static final int EXTH_ISBN = 104;
    protected static final int EXTH_SUBJECT = 105;
    protected static final int EXTH_PUBLISHED_DATE = 106;
    protected static final int EXTH_LANGUAGE = 524;
    protected static final int EXTH_COVER_OFFSET = 201;
    protected static final int EXTH_THUMB_OFFSET = 202;
    protected static final int EXTH_ASIN = 113;
    private static final Pattern DATE_SLASH_FORMAT_PATTERN = Pattern.compile("\\d{4}/\\d{2}/\\d{2}");
    private static final Pattern YEAR_ONLY_PATTERN = Pattern.compile("\\d{4}");
    private static final Pattern DATE_DASH_FORMAT_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern CATEGORY_SEPARATOR_PATTERN = Pattern.compile("[;,]");
    private static final Pattern ISBN_INVALID_CHARS_PATTERN = Pattern.compile("[^0-9Xx]");

    protected abstract String getFormatName();

    @Override
    public byte[] extractCover(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            PalmDB palmDB = readPalmDB(raf);
            if (palmDB == null) {
                return null;
            }

            MobiHeader mobiHeader = readMobiHeader(raf, palmDB);
            if (mobiHeader == null) {
                return null;
            }

            // Try to get cover from EXTH
            Integer coverIndex = mobiHeader.exthRecords.get(EXTH_COVER_OFFSET);
            if (coverIndex == null) {
                coverIndex = mobiHeader.exthRecords.get(EXTH_THUMB_OFFSET);
            }

            if (coverIndex != null) {
                int imageRecordIndex = mobiHeader.firstImageIndex + coverIndex;
                if (imageRecordIndex < palmDB.records.size()) {
                    return extractImageFromRecord(raf, palmDB.records.get(imageRecordIndex));
                }
            }

            // Try first image
            if (mobiHeader.firstImageIndex > 0 && mobiHeader.firstImageIndex < palmDB.records.size()) {
                return extractImageFromRecord(raf, palmDB.records.get(mobiHeader.firstImageIndex));
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to extract cover from {}: {}", getFormatName(), file.getName(), e);
            return null;
        }
    }

    @Override
    public BookMetadata extractMetadata(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            PalmDB palmDB = readPalmDB(raf);
            if (palmDB == null) {
                log.warn("Failed to read PalmDB header from: {}", file.getName());
                return null;
            }

            MobiHeader mobiHeader = readMobiHeader(raf, palmDB);
            if (mobiHeader == null) {
                log.warn("Failed to read {} header from: {}", getFormatName(), file.getName());
                return null;
            }

            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();
            List<String> authors = new ArrayList<>();
            Set<String> categories = new HashSet<>();

            // Extract title
            if (StringUtils.isNotBlank(mobiHeader.title)) {
                builder.title(mobiHeader.title);
            }

            // Extract metadata from EXTH records
            for (var entry : mobiHeader.exthData.entrySet()) {
                int recordType = entry.getKey();
                String value = entry.getValue();

                if (StringUtils.isBlank(value)) {
                    continue;
                }

                switch (recordType) {
                    case EXTH_AUTHOR -> authors.add(value.trim());
                    case EXTH_PUBLISHER -> builder.publisher(value.trim());
                    case EXTH_DESCRIPTION -> builder.description(value.trim());
                    case EXTH_ISBN -> {
                        String isbn = ISBN_INVALID_CHARS_PATTERN.matcher(value).replaceAll("");
                        if (isbn.length() == 13) {
                            builder.isbn13(isbn);
                        } else if (isbn.length() == 10) {
                            builder.isbn10(isbn);
                        }
                    }
                    case EXTH_SUBJECT -> {
                        for (String category : CATEGORY_SEPARATOR_PATTERN.split(value)) {
                            String trimmed = category.trim();
                            if (StringUtils.isNotBlank(trimmed)) {
                                categories.add(trimmed);
                            }
                        }
                    }
                    case EXTH_PUBLISHED_DATE -> {
                        LocalDate date = parseDate(value.trim());
                        if (date != null) {
                            builder.publishedDate(date);
                        }
                    }
                    case EXTH_LANGUAGE -> builder.language(value.trim());
                    case EXTH_ASIN -> builder.asin(value.trim());
                }
            }

            builder.authors(authors);
            builder.categories(categories);

            return builder.build();
        } catch (Exception e) {
            log.error("Failed to extract metadata from {}: {}", getFormatName(), file.getName(), e);
            return null;
        }
    }

    protected PalmDB readPalmDB(RandomAccessFile raf) throws IOException {
        if (raf.length() < 78) {
            log.debug("File too small for PalmDB");
            return null;
        }

        PalmDB palmDB = new PalmDB();

        // Read database name
        raf.seek(0);
        byte[] nameBytes = new byte[32];
        raf.readFully(nameBytes);
        int nameLen = 0;
        for (int i = 0; i < nameBytes.length; i++) {
            if (nameBytes[i] == 0) {
                nameLen = i;
                break;
            }
        }
        palmDB.name = new String(nameBytes, 0, nameLen, StandardCharsets.ISO_8859_1);

        // Read number of records
        raf.seek(76);
        palmDB.numRecords = readShort(raf);

        log.debug("PalmDB: name={}, records={}", palmDB.name, palmDB.numRecords);

        if (palmDB.numRecords <= 0 || palmDB.numRecords > 10000) {
            log.debug("Invalid number of records: {}", palmDB.numRecords);
            return null;
        }

        // Read record list
        raf.seek(78);
        for (int i = 0; i < palmDB.numRecords; i++) {
            PalmDBRecord record = new PalmDBRecord();
            record.offset = readInt(raf);
            record.attributes = raf.read();
            record.id = readThreeBytes(raf);
            palmDB.records.add(record);
        }

        // Calculate record sizes
        for (int i = 0; i < palmDB.records.size(); i++) {
            PalmDBRecord record = palmDB.records.get(i);
            if (i < palmDB.records.size() - 1) {
                record.size = palmDB.records.get(i + 1).offset - record.offset;
            } else {
                record.size = (int) (raf.length() - record.offset);
            }
        }

        return palmDB;
    }

    protected MobiHeader readMobiHeader(RandomAccessFile raf, PalmDB palmDB) throws IOException {
        if (palmDB.records.isEmpty()) {
            return null;
        }

        PalmDBRecord record0 = palmDB.records.get(0);
        raf.seek(record0.offset);

        MobiHeader header = new MobiHeader();

        // Read PalmDOC header
        int compression = readShort(raf);
        raf.skipBytes(2);
        int textLength = readInt(raf);
        int recordCount = readShort(raf);
        int recordSize = readShort(raf);
        int encryptionType = readShort(raf);
        raf.skipBytes(2);

        log.debug("PalmDOC: compression={}, textLength={}, recordCount={}, recordSize={}",
                  compression, textLength, recordCount, recordSize);

        // Check for MOBI header
        raf.seek(record0.offset + 16);
        byte[] identifier = new byte[4];
        raf.readFully(identifier);
        String identifierStr = new String(identifier, StandardCharsets.US_ASCII);

        log.debug("Header identifier: {}", identifierStr);

        if (!identifierStr.equals("MOBI")) {
            log.debug("No MOBI/KF8 header found");
            return null;
        }

        // Read MOBI header
        raf.seek(record0.offset + 20);
        int headerLength = readInt(raf);
        int mobiType = readInt(raf);
        int textEncoding = readInt(raf);

        log.debug("{}: headerLength={}, type={}, encoding={}", getFormatName(), headerLength, mobiType, textEncoding);

        // Check if this is KF8 (AZW3) format
        header.isKF8 = (mobiType == 2);
        if (header.isKF8) {
            log.debug("Detected KF8 (AZW3) format");
        }

        // Process format-specific logic
        processFormatSpecificHeader(raf, record0, header, headerLength);

        // Determine charset
        Charset charset = textEncoding == 65001 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
        header.charset = charset;

        // Skip to full name offset/length
        raf.seek(record0.offset + 16 + 68);
        int fullNameOffset = readInt(raf);
        int fullNameLength = readInt(raf);

        log.debug("Full name: offset={}, length={}", fullNameOffset, fullNameLength);

        // Read title
        if (fullNameLength > 0 && fullNameLength < 10000) {
            raf.seek(record0.offset + fullNameOffset);
            byte[] titleBytes = new byte[fullNameLength];
            raf.readFully(titleBytes);
            header.title = new String(titleBytes, charset).trim();
            log.debug("Title: {}", header.title);
        }

        // Read first image index
        raf.seek(record0.offset + 16 + 92);
        header.firstImageIndex = readInt(raf);

        log.debug("First image index: {}", header.firstImageIndex);

        // Check for EXTH header
        raf.seek(record0.offset + 16 + 112);
        int exthFlags = readInt(raf);

        log.debug("EXTH flags: 0x{}", Integer.toHexString(exthFlags));

        if ((exthFlags & 0x40) != 0) {
            long exthOffset = record0.offset + 16 + headerLength;
            raf.seek(exthOffset);

            byte[] exthIdentifier = new byte[4];
            raf.readFully(exthIdentifier);
            String exthStr = new String(exthIdentifier, StandardCharsets.US_ASCII);

            log.debug("EXTH identifier: {}", exthStr);

            if (exthStr.equals("EXTH")) {
                readExthRecords(raf, header, charset);
            }
        }

        return header;
    }

    protected void processFormatSpecificHeader(RandomAccessFile raf, PalmDBRecord record0,
                                               MobiHeader header, int headerLength) throws IOException {
        // Override in subclasses for format-specific processing
    }

    protected void readExthRecords(RandomAccessFile raf, MobiHeader header, Charset charset) throws IOException {
        long exthStart = raf.getFilePointer() - 4;

        int headerLength = readInt(raf);
        int recordCount = readInt(raf);

        log.debug("EXTH: headerLength={}, recordCount={}", headerLength, recordCount);

        for (int i = 0; i < recordCount && i < 500; i++) {
            int recordType = readInt(raf);
            int recordLength = readInt(raf);

            if (recordLength < 8 || recordLength > 100000) {
                log.warn("Invalid EXTH record length: {}", recordLength);
                break;
            }

            int dataLength = recordLength - 8;
            byte[] data = new byte[dataLength];
            raf.readFully(data);

            if (recordType == EXTH_COVER_OFFSET || recordType == EXTH_THUMB_OFFSET) {
                if (dataLength >= 4) {
                    int value = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                               ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    header.exthRecords.put(recordType, value);
                    log.debug("EXTH record {} (int): {}", recordType, value);
                }
            } else {
                String value = new String(data, charset).trim();
                value = value.replace("\0", "");
                if (!value.isEmpty()) {
                    header.exthData.put(recordType, value);
                    log.debug("EXTH record {} (string): {}", recordType,
                             value.length() > 100 ? value.substring(0, 100) + "..." : value);
                }
            }
        }
    }

    protected byte[] extractImageFromRecord(RandomAccessFile raf, PalmDBRecord record) throws IOException {
        if (record.size <= 0 || record.size > 10_000_000) {
            return null;
        }

        raf.seek(record.offset);
        byte[] data = new byte[record.size];
        raf.readFully(data);

        // Check for image magic bytes
        if (data.length > 4) {
            if ((data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) || // JPEG
                (data[0] == (byte) 0x89 && data[1] == (byte) 0x50 &&
                 data[2] == (byte) 0x4E && data[3] == (byte) 0x47) || // PNG
                (data[0] == (byte) 0x47 && data[1] == (byte) 0x49 &&
                 data[2] == (byte) 0x46)) { // GIF
                return data;
            }
        }

        return null;
    }

    protected LocalDate parseDate(String dateString) {
        if (StringUtils.isBlank(dateString)) {
            return null;
        }

        try {
            if (DATE_DASH_FORMAT_PATTERN.matcher(dateString).matches()) {
                return LocalDate.parse(dateString);
            }

            if (YEAR_ONLY_PATTERN.matcher(dateString).matches()) {
                return LocalDate.of(Integer.parseInt(dateString), 1, 1);
            }

            if (DATE_SLASH_FORMAT_PATTERN.matcher(dateString).matches()) {
                String[] parts = dateString.split("/");
                return LocalDate.of(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
            }
        } catch (Exception e) {
            log.debug("Failed to parse date: {}", dateString, e);
        }

        return null;
    }

    protected int readInt(RandomAccessFile raf) throws IOException {
        return (raf.read() << 24) | (raf.read() << 16) | (raf.read() << 8) | raf.read();
    }

    protected int readShort(RandomAccessFile raf) throws IOException {
        return (raf.read() << 8) | raf.read();
    }

    protected int readThreeBytes(RandomAccessFile raf) throws IOException {
        return (raf.read() << 16) | (raf.read() << 8) | raf.read();
    }

    protected static class PalmDB {
        String name;
        int numRecords;
        List<PalmDBRecord> records = new ArrayList<>();
    }

    protected static class PalmDBRecord {
        int offset;
        int attributes;
        int id;
        int size;
    }

    protected static class MobiHeader {
        String title;
        int firstImageIndex = -1;
        boolean isKF8 = false;
        Charset charset = StandardCharsets.UTF_8;
        Map<Integer, String> exthData = new HashMap<>();
        Map<Integer, Integer> exthRecords = new HashMap<>();
    }
}

