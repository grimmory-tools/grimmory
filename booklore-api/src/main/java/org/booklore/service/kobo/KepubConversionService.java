package org.booklore.service.kobo;

import org.booklore.util.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KepubConversionService {

    private final FileService fileService;

    public File convertEpubToKepub(File epubFile, File tempDir, boolean forceEnableHyphenation) throws IOException, InterruptedException {
        validateInputs(epubFile);

        Path kepubifyBinary = fileService.findSystemFile("kepubify");

        if (kepubifyBinary == null) {
            throw new IOException("Kepubify conversion failed: could not find kepubify binary");
        }

        File outputFile = executeKepubifyConversion(epubFile, tempDir, kepubifyBinary, forceEnableHyphenation);

        log.info("Successfully converted {} to {} (size: {} bytes)", epubFile.getName(), outputFile.getName(), outputFile.length());
        return outputFile;
    }

    private void validateInputs(File epubFile) {
        if (epubFile == null || !epubFile.isFile() || !epubFile.getName().endsWith(".epub")) {
            throw new IllegalArgumentException("Invalid EPUB file: " + epubFile);
        }
    }

    private File executeKepubifyConversion(File epubFile, File tempDir, Path kepubifyBinary, boolean forceEnableHyphenation) throws IOException, InterruptedException {
        ProcessBuilder pb;

        if (forceEnableHyphenation)
            pb = new ProcessBuilder(kepubifyBinary.toAbsolutePath().toString(), "--hyphenate", "-o", tempDir.getAbsolutePath(), epubFile.getAbsolutePath());
        else
            pb = new ProcessBuilder(kepubifyBinary.toAbsolutePath().toString(), "-o", tempDir.getAbsolutePath(), epubFile.getAbsolutePath());

        pb.directory(tempDir);

        log.info("Starting kepubify conversion for {} -> output dir: {}", epubFile.getAbsolutePath(), tempDir.getAbsolutePath());

        Process process = pb.start();

        String output = readProcessOutput(process.getInputStream());
        String error = readProcessOutput(process.getErrorStream());

        int exitCode = process.waitFor();
        logProcessResults(exitCode, output, error);

        if (exitCode != 0) {
            throw new IOException(String.format("Kepubify conversion failed with exit code: %d. Error: %s", exitCode, error));
        }

        return findOutputFile(tempDir);
    }

    private String readProcessOutput(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Error reading process output: {}", e.getMessage());
            return "";
        }
    }

    private void logProcessResults(int exitCode, String output, String error) {
        log.debug("Kepubify process exited with code {}", exitCode);
        if (!output.isEmpty()) {
            log.debug("Kepubify stdout: {}", output);
        }
        if (!error.isEmpty()) {
            log.error("Kepubify stderr: {}", error);
        }
    }

    private File findOutputFile(File tempDir) throws IOException {
        File[] kepubFiles = tempDir.listFiles((dir, name) -> name.endsWith(".kepub.epub"));
        if (kepubFiles == null || kepubFiles.length == 0) {
            throw new IOException("Kepubify conversion completed but no .kepub.epub file was created in: " + tempDir.getAbsolutePath());
        }
        return kepubFiles[0];
    }
}
