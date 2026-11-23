package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.ArchiveService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NasEpubPrototype {
    private static final long MAX_INPUT_BYTES = 64L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_REQUEST_BYTES = 6 * 1024 * 1024;
    private static final int MAX_COVER_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BATCH_SIZE = 100;
    private static final String TEST_MARKER = "META-INF/grimmory-nas-prototype.txt";
    private static final byte[] TEST_MARKER_CONTENT = "synthetic-test-only-v1".getBytes(StandardCharsets.UTF_8);

    public record Request(String input, String output, String inputSha256, String title, List<String> authors,
                          BookMetadata metadata, MetadataClearFlags clearFlags, byte[] cover) {
        public Request(String input, String output, String inputSha256, String title, List<String> authors) {
            this(input, output, inputSha256, title, authors, null, null, null);
        }
    }
    public record Result(String output, String outputSha256) {}
    public record BatchRequest(List<Request> requests) {}
    public record BatchResult(List<Result> results) {}

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new IllegalArgumentException("The prototype only operates in /sandbox");
        }
        PrintStream resultStream = System.out;
        System.setOut(System.err);
        run(System.in, resultStream, Path.of("/sandbox"));
    }

    static void run(InputStream input, OutputStream output, Path sandbox) throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Request request = mapper.readValue(readRequest(input), Request.class);
        Result result = process(sandbox, request);
        output.write(mapper.writeValueAsBytes(result));
        output.write('\n');
    }

    static void runBatch(InputStream input, OutputStream output, Path sandbox) throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        BatchRequest request = mapper.readValue(readRequest(input), BatchRequest.class);
        BatchResult result = processBatch(sandbox, request);
        output.write(mapper.writeValueAsBytes(result));
        output.write('\n');
    }

    static byte[] readRequest(InputStream input) throws IOException {
        byte[] requestBytes = input.readNBytes(MAX_REQUEST_BYTES + 1);
        if (requestBytes.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request is too large");
        }
        return requestBytes;
    }

    static BatchResult processBatch(Path sandbox, BatchRequest batch) throws IOException {
        validateBatch(batch);
        Path root = sandbox.toRealPath();
        List<Result> results = new ArrayList<>();
        try {
            for (Request request : batch.requests()) {
                results.add(process(root, request));
            }
            return new BatchResult(List.copyOf(results));
        } catch (IOException | RuntimeException e) {
            for (Result result : results) {
                try {
                    Files.delete(root.resolve(result.output()));
                } catch (IOException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            throw e;
        }
    }

    static void validateBatch(BatchRequest batch) {
        if (batch == null || batch.requests() == null || batch.requests().isEmpty()
                || batch.requests().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch must contain between 1 and 100 requests");
        }
        Set<String> inputs = new HashSet<>();
        Set<String> outputs = new HashSet<>();
        for (Request request : batch.requests()) {
            if (request == null) {
                throw new IllegalArgumentException("Batch request cannot be null");
            }
            String input = fileName(request.input()).toLowerCase(Locale.ROOT);
            String output = fileName(request.output()).toLowerCase(Locale.ROOT);
            if (!inputs.add(input) || !outputs.add(output)) {
                throw new IllegalArgumentException("Batch inputs and outputs must be distinct");
            }
        }
        if (!Collections.disjoint(inputs, outputs)) {
            throw new IllegalArgumentException("Batch outputs must not overwrite any input");
        }
    }

    public static Result process(Path sandbox, Request request) throws IOException {
        if (request == null || request.inputSha256() == null
                || !request.inputSha256().matches("[a-fA-F0-9]{64}")) {
            throw new IllegalArgumentException("A valid input SHA-256 is required");
        }
        String inputName = fileName(request.input());
        String outputName = fileName(request.output());
        if (inputName.equalsIgnoreCase(outputName)) {
            throw new IllegalArgumentException("Input and output must be different");
        }
        if (request.metadata() != null && (request.title() != null || request.authors() != null)) {
            throw new IllegalArgumentException("Use either full metadata or title and authors");
        }
        if (request.metadata() == null && request.clearFlags() != null) {
            throw new IllegalArgumentException("Clear flags require full metadata");
        }
        if (request.cover() != null && (request.cover().length == 0 || request.cover().length > MAX_COVER_BYTES)) {
            throw new IllegalArgumentException("Invalid cover size");
        }
        if (request.metadata() == null && request.title() == null && request.authors() == null) {
            throw new IllegalArgumentException("A title or authors are required");
        }
        if (request.title() != null && (request.title().isBlank() || request.title().length() > 1024)) {
            throw new IllegalArgumentException("Invalid title");
        }
        if (request.authors() != null && (request.authors().isEmpty() || request.authors().size() > 50
                || request.authors().stream().anyMatch(a -> a == null || a.isBlank() || a.length() > 512))) {
            throw new IllegalArgumentException("Invalid authors");
        }

        Path root = sandbox.toRealPath();
        Path input = root.resolve(inputName);
        Path output = root.resolve(outputName);
        if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Input must be a regular file, not a symbolic link");
        }
        if (Files.size(input) > MAX_INPUT_BYTES) {
            throw new IOException("Input EPUB is too large");
        }
        if (!sha256(input).equalsIgnoreCase(request.inputSha256())) {
            throw new IOException("Input SHA-256 mismatch");
        }
        validateArchive(input);

        BookMetadataEntity metadata;
        if (request.metadata() != null) {
            metadata = NasEpubMetadataMapper.INSTANCE.restore(request.metadata());
        } else {
            metadata = new BookMetadataEntity();
            metadata.setTitle(request.title());
            metadata.setAuthors(request.authors() == null ? null : request.authors().stream()
                    .map(name -> AuthorEntity.builder().name(name).build()).toList());
            metadata.setCategories(null);
            metadata.setMoods(null);
            metadata.setTags(null);
        }

        Path temporary = Files.createTempFile(root, ".grimmory-agent-", ".epub");
        boolean outputCreated = false;
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (!sha256(temporary).equalsIgnoreCase(request.inputSha256())) {
                throw new IOException("Input changed during copying");
            }
            EpubMetadataWriter writer = new EpubMetadataWriter(
                    MetadataPersistenceSettings::new, new ArchiveService());
            if (request.cover() != null) {
                writer.writeCoverToFile(temporary.toFile(), request.cover());
            }
            if (request.metadata() == null) {
                writer.writeTitleAndAuthorsToFile(temporary.toFile(), metadata);
            } else {
                writer.writeMetadataToFile(temporary.toFile(), metadata, request.clearFlags());
            }
            validateArchive(temporary);
            String outputHash = sha256(temporary);
            try (OutputStream stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                outputCreated = true;
                Files.copy(temporary, stream);
            }
            Files.delete(temporary);
            return new Result(outputName, outputHash);
        } catch (IOException | RuntimeException e) {
            if (outputCreated) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    static String fileName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,150}")
                || !name.toLowerCase(Locale.ROOT).endsWith(".epub")) {
            throw new IllegalArgumentException("Only simple EPUB filenames are accepted");
        }
        return name;
    }

    static void validateArchive(Path path) throws IOException {
        long expanded = 0;
        int count = 0;
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry marker = zip.getEntry(TEST_MARKER);
            if (marker == null || marker.isDirectory()) {
                throw new IOException("Only marked synthetic EPUBs are accepted");
            }
            try (InputStream stream = zip.getInputStream(marker)) {
                if (!MessageDigest.isEqual(stream.readNBytes(TEST_MARKER_CONTENT.length + 1), TEST_MARKER_CONTENT)) {
                    throw new IOException("Invalid synthetic EPUB marker");
                }
            }
            var entries = zip.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (++count > MAX_ENTRIES || !names.add(name) || name.startsWith("/")
                        || name.contains("\\") || name.contains(":") || name.contains("//")
                        || List.of(name.split("/")).contains("..") || List.of(name.split("/")).contains(".")) {
                    throw new IOException("Invalid EPUB archive entry");
                }
                if (entry.isDirectory()) continue;
                CRC32 checksum = new CRC32();
                try (InputStream stream = zip.getInputStream(entry)) {
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        expanded += read;
                        if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Expanded EPUB is too large");
                        checksum.update(buffer, 0, read);
                    }
                }
                if (entry.getCrc() != checksum.getValue()) throw new IOException("EPUB entry checksum mismatch");
            }
        }
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        try (InputStream stream = new DigestInputStream(Files.newInputStream(path), digest)) {
            stream.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
