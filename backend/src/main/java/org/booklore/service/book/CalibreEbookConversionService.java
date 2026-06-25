package org.booklore.service.book;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.enums.BookFileType;
import org.booklore.util.FileService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalibreEbookConversionService {

    public static final List<BookFileType> SUPPORTED_TARGET_FORMATS = List.of(
            BookFileType.EPUB,
            BookFileType.PDF,
            BookFileType.MOBI,
            BookFileType.AZW3,
            BookFileType.FB2
    );

    private static final Duration CONVERSION_TIMEOUT = Duration.ofMinutes(30);

    private final FileService fileService;

    public boolean isAvailable() {
        Path binary = fileService.findSystemFile("ebook-convert");
        return binary != null && Files.isExecutable(binary);
    }

    public Path convert(Path inputFile, Path outputFile) throws IOException, InterruptedException {
        validateInputFile(inputFile);

        Path binary = fileService.findSystemFile("ebook-convert");
        if (binary == null || !Files.isExecutable(binary)) {
            throw new IOException("Calibre ebook-convert is not available");
        }

        Path outputParent = outputFile.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        Process process = new ProcessBuilder(
                binary.toAbsolutePath().toString(),
                inputFile.toString(),
                outputFile.toString())
                .redirectErrorStream(true)
                .start();

        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process.getInputStream()));
        boolean finished = process.waitFor(CONVERSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Calibre ebook-convert timed out");
        }

        String capturedOutput = outputFuture.join();
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("Calibre ebook-convert failed with exit code " + exitCode + ": " + capturedOutput);
        }

        if (!Files.exists(outputFile) || Files.size(outputFile) == 0L) {
            throw new IOException("Calibre ebook-convert did not create an output file");
        }

        log.info("Converted ebook {} to {}", inputFile, outputFile);
        return outputFile;
    }

    private void validateInputFile(Path inputFile) throws IOException {
        if (inputFile == null || !Files.isRegularFile(inputFile) || !Files.isReadable(inputFile)) {
            throw new IOException("Input file is not a readable regular file");
        }
    }

    private String readProcessOutput(InputStream inputStream) {
        try (inputStream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            inputStream.transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
