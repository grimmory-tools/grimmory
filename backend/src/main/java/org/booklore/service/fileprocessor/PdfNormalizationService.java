package org.booklore.service.fileprocessor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Service to normalize PDF files using qpdf.
 * This fixes coordinate displacement issues by resetting the PDF's internal maps and CropBox offsets.
 */
@Slf4j
@Service
public class PdfNormalizationService {

    /**
     * Normalizes the given PDF file in-place using qpdf.
     * 
     * @param pdfFile The file to normalize.
     * @return true if normalization was successful or not needed, false otherwise.
     */
    public boolean normalizeInPlace(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            return false;
        }

        log.info("Normalizing PDF coordinate system for: {}", pdfFile.getName());

        try {
            // --replace-input: modifies the file in place
            // --pages . 1-z --: reads all pages and writes them back, forcing map regeneration
            ProcessBuilder pb = new ProcessBuilder(
                "qpdf", "--replace-input", "--pages", ".", "1-z", "--", pdfFile.getAbsolutePath()
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(2, TimeUnit.MINUTES);
            if (!finished) {
                log.warn("PDF normalization timed out for: {}", pdfFile.getName());
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("Successfully normalized PDF: {}", pdfFile.getName());
                return true;
            } else {
                log.warn("qpdf exited with code {} for file: {}", exitCode, pdfFile.getName());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to execute qpdf for normalization: {}", e.getMessage());
            return false;
        }
    }
}
