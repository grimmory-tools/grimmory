package org.grimmory.service.kobo;

import org.grimmory.model.entity.AuthorEntity;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.CategoryEntity;
import org.grimmory.model.entity.TagEntity;
import org.grimmory.service.ArchiveService;
import org.grimmory.util.ArchiveUtils;
import org.grimmory.util.FileService;
import org.grimmory.util.MimeDetector;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Service for converting comic book archive files (CBX) to EPUB format.
 * <p>
 * This service supports the following comic book archive formats:
 * <ul>
 * <li><b>CBZ</b> - Comic book ZIP archive</li>
 * <li><b>CBR</b> - Comic book RAR archive</li>
 * <li><b>CB7</b> - Comic book 7z archive</li>
 * </ul>
 * </p>
 * <p>
 * Supported image formats within archives: JPG, JPEG, PNG, WEBP, GIF, BMP
 * </p>
 * 
 * <h3>Size Limits</h3>
 * <ul>
 * <li>Maximum individual image size: 50 MB</li>
 * </ul>
 * 
 * @see KepubConversionService
 */
@Slf4j
@Service
public class CbxConversionService {

    private static final String IMAGE_ROOT_PATH = "OEBPS/Images/";
    private static final String HTML_ROOT_PATH = "OEBPS/Text/";
    private static final String CONTENT_OPF_PATH = "OEBPS/content.opf";
    private static final String NAV_XHTML_PATH = "OEBPS/nav.xhtml";
    private static final String TOC_NCX_PATH = "OEBPS/toc.ncx";
    private static final String STYLESHEET_CSS_PATH = "OEBPS/Styles/stylesheet.css";
    private static final String COVER_IMAGE_PATH = "OEBPS/Images/cover.jpg";
    private static final String MIMETYPE_CONTENT = "application/epub+zip";
    private static final long MAX_IMAGE_SIZE_BYTES = 50L * 1024 * 1024;
    private static final String EXTRACTED_IMAGES_SUBDIR = "cbx_extracted_images";

    private final Configuration freemarkerConfig;
    private final ArchiveService archiveService;

    public CbxConversionService(ArchiveService archiveService) {
        this.freemarkerConfig = initializeFreemarkerConfiguration();
        this.archiveService = archiveService;
    }

    public record EpubContentFileGroup(String contentKey, String imagePath, String htmlPath) {
    }

    /**
     * Converts a comic book archive (CBZ, CBR, or CB7) to EPUB format.
     * <p>
     * The conversion process:
     * <ol>
     * <li>Extracts all images from the archive to a temporary directory</li>
     * <li>Creates an EPUB structure with one XHTML page per image</li>
     * <li>Includes proper EPUB metadata from the book entity</li>
     * <li>JPEG images are passed through directly; other formats are converted to
     * JPEG (85% quality)</li>
     * </ol>
     * </p>
     * 
     * @param cbxFile    the comic book archive file (must be CBZ, CBR, or CB7)
     * @param tempDir    the temporary directory where the output EPUB will be
     *                   created
     * @param bookEntity the book metadata to include in the EPUB
     * @return the converted EPUB file
     * @throws IOException              if file I/O operations fail
     * @throws TemplateException        if EPUB template processing fails
     * @throws IllegalArgumentException if the file format is not supported
     * @throws IllegalStateException    if no valid images are found in the archive
     */
    public File convertCbxToEpub(File cbxFile, File tempDir, BookEntity bookEntity, int compressionPercentage)
            throws IOException, TemplateException {
        validateInputs(cbxFile, tempDir);

        log.info("Starting CBX to EPUB conversion for: {}", cbxFile.getName());

        File outputFile = executeCbxConversion(cbxFile, tempDir, bookEntity, compressionPercentage);

        log.info("Successfully converted {} to {} (size: {} bytes)",
                cbxFile.getName(), outputFile.getName(), outputFile.length());
        return outputFile;
    }

    private File executeCbxConversion(File cbxFile, File tempDir, BookEntity bookEntity, int compressionPercentage)
            throws IOException, TemplateException {

        Path epubFilePath = Paths.get(tempDir.getAbsolutePath(), cbxFile.getName() + ".epub");
        File epubFile = epubFilePath.toFile();

        Path extractedImagesDir = Paths.get(tempDir.getAbsolutePath(), EXTRACTED_IMAGES_SUBDIR);
        Files.createDirectories(extractedImagesDir);

        List<Path> imagePaths = extractImagesFromCbx(cbxFile, extractedImagesDir);
        if (imagePaths.isEmpty()) {
            throw new IllegalStateException("No valid images found in CBX file: " + cbxFile.getName());
        }

        log.debug("Extracted {} images from CBX file to disk", imagePaths.size());

        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new FileOutputStream(epubFile))) {
            addMimetypeEntry(zipOut);
            addMetaInfContainer(zipOut);
            addStylesheet(zipOut);

            List<EpubContentFileGroup> contentGroups = addImagesAndPages(zipOut, imagePaths, compressionPercentage);

            addContentOpf(zipOut, bookEntity, contentGroups);
            addTocNcx(zipOut, bookEntity, contentGroups);
            addNavXhtml(zipOut, bookEntity, contentGroups);
        }

        deleteDirectory(extractedImagesDir);

        return epubFile;
    }

    private void deleteDirectory(Path directory) {
        try {
            FileSystemUtils.deleteRecursively(directory);
        } catch (IOException e) {
            log.warn("Failed to delete directory {}: {}", directory, e.getMessage());
        }
    }

    private void validateInputs(File cbxFile, File tempDir) {
        if (cbxFile == null || !cbxFile.isFile()) {
            throw new IllegalArgumentException("Invalid CBX file: " + cbxFile);
        }

        ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(cbxFile);
        if (type == ArchiveUtils.ArchiveType.UNKNOWN) {
            throw new IllegalArgumentException("Unsupported file format: " + cbxFile.getName() +
                    ". Supported formats: CBZ, CBR, CB7");
        }

        if (tempDir == null || !tempDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid temp directory: " + tempDir);
        }
    }

    private Configuration initializeFreemarkerConfiguration() {
        Configuration config = new Configuration(Configuration.VERSION_2_3_33);
        config.setTemplateLoader(new ClassTemplateLoader(this.getClass(), "/templates/epub"));
        config.setDefaultEncoding(StandardCharsets.UTF_8.name());
        config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        config.setLogTemplateExceptions(false);
        config.setWrapUncheckedExceptions(true);
        return config;
    }

    private List<Path> extractImagesFromCbx(File cbxFile, Path extractedImagesDir) throws IOException {
        List<Path> imagePaths = new ArrayList<>();

        for (ArchiveService.Entry entry : archiveService.getEntries(cbxFile.toPath())) {
            if (!isImageFile(entry.name())) {
                continue;
            }

            validateImageSize(entry.name(), entry.size());

            try {
                Path outputPath = extractedImagesDir.resolve(extractFileName(entry.name()));

                archiveService.extractEntryToPath(cbxFile.toPath(), entry.name(), outputPath);

                imagePaths.add(outputPath);
            } catch (Exception e) {
                log.warn("Error extracting image {}: {}", entry.name(), e.getMessage());
            }
        }

        log.debug("Found {} image entries in CBR file", imagePaths.size());
        imagePaths.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()));
        return imagePaths;
    }

    private String extractFileName(String entryPath) {
        return Path.of(entryPath).getFileName().toString();
    }

    private void validateImageSize(String imageName, long size) throws IOException {
        if (size > MAX_IMAGE_SIZE_BYTES) {
            throw new IOException(String.format("Image '%s' exceeds maximum size limit: %d bytes (max: %d bytes)",
                    imageName, size, MAX_IMAGE_SIZE_BYTES));
        }
    }

    private boolean isImageFile(String fileName) {
        if (shouldIgnoreEntry(fileName)) {
            return false;
        }

        String lowerName = fileName.toLowerCase();

        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".webp") ||
                lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") ||
                lowerName.endsWith(".avif") || lowerName.endsWith(".heic");
    }

    private boolean shouldIgnoreEntry(String entryName) {
        if (entryName.contains("__MACOSX")) {
            return true;
        }

        String fileName = entryName;
        int lastSlash = entryName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = entryName.substring(lastSlash + 1);
        }

        return fileName.startsWith("._");
    }

    private boolean isJpegFile(Path path) {
        try {
            String mime = MimeDetector.detect(path);
            return "image/jpeg".equals(mime);
        } catch (IOException e) {
            log.debug("Failed to detect MIME for {}: {}", path, e.getMessage());
            return false;
        }
    }

    private void addMimetypeEntry(ZipArchiveOutputStream zipOut) throws IOException {
        byte[] mimetypeBytes = MIMETYPE_CONTENT.getBytes(StandardCharsets.UTF_8);
        ZipArchiveEntry mimetypeEntry = new ZipArchiveEntry("mimetype");
        mimetypeEntry.setMethod(ZipArchiveEntry.STORED);
        mimetypeEntry.setSize(mimetypeBytes.length);
        mimetypeEntry.setCrc(calculateCrc32(mimetypeBytes));

        zipOut.putArchiveEntry(mimetypeEntry);
        zipOut.write(mimetypeBytes);
        zipOut.closeArchiveEntry();
    }

    private void addMetaInfContainer(ZipArchiveOutputStream zipOut) throws IOException, TemplateException {
        Map<String, Object> model = new HashMap<>();
        model.put("contentOpfPath", CONTENT_OPF_PATH);

        String containerXml = processTemplate("xml/container.xml.ftl", model);

        ZipArchiveEntry containerEntry = new ZipArchiveEntry("META-INF/container.xml");
        zipOut.putArchiveEntry(containerEntry);
        zipOut.write(containerXml.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private void addStylesheet(ZipArchiveOutputStream zipOut) throws IOException {
        String stylesheetContent = loadResourceAsString("/templates/epub/css/stylesheet.css");

        ZipArchiveEntry stylesheetEntry = new ZipArchiveEntry(STYLESHEET_CSS_PATH);
        zipOut.putArchiveEntry(stylesheetEntry);
        zipOut.write(stylesheetContent.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private List<EpubContentFileGroup> addImagesAndPages(ZipArchiveOutputStream zipOut, List<Path> imagePaths,
            int compressionPercentage)
            throws IOException, TemplateException {

        List<EpubContentFileGroup> contentGroups = new ArrayList<>();

        if (!imagePaths.isEmpty()) {
            addImageToZipFromPath(zipOut, COVER_IMAGE_PATH, imagePaths.getFirst(), compressionPercentage);
        }

        for (int i = 0; i < imagePaths.size(); i++) {
            Path imageSourcePath = imagePaths.get(i);
            String contentKey = String.format("page-%04d", i + 1);
            String imageFileName = contentKey + ".jpg";
            String htmlFileName = contentKey + ".xhtml";

            String imagePath = IMAGE_ROOT_PATH + imageFileName;
            String htmlPath = HTML_ROOT_PATH + htmlFileName;

            addImageToZipFromPath(zipOut, imagePath, imageSourcePath, compressionPercentage);

            String htmlContent = generatePageHtml(imageFileName, i + 1);
            ZipArchiveEntry htmlEntry = new ZipArchiveEntry(htmlPath);
            zipOut.putArchiveEntry(htmlEntry);
            zipOut.write(htmlContent.getBytes(StandardCharsets.UTF_8));
            zipOut.closeArchiveEntry();

            contentGroups.add(new EpubContentFileGroup(contentKey, imagePath, htmlPath));
        }

        return contentGroups;
    }

    private void addImageToZipFromPath(ZipArchiveOutputStream zipOut, String epubImagePath, Path sourceImagePath,
            int compressionPercentage)
            throws IOException {
        ZipArchiveEntry imageEntry = new ZipArchiveEntry(epubImagePath);
        zipOut.putArchiveEntry(imageEntry);

        if (isJpegFile(sourceImagePath)) {
            try (InputStream fis = Files.newInputStream(sourceImagePath)) {
                fis.transferTo(zipOut);
            }
        } else {
            try (InputStream fis = Files.newInputStream(sourceImagePath)) {
                BufferedImage image = null;
                try {
                    image = FileService.readImage(fis);
                } catch (Exception e) {
                    log.debug("Failed to decode image {} with FileService: {}", sourceImagePath, e.getMessage());
                }

                if (image != null) {
                    writeJpegImage(image, zipOut, compressionPercentage / 100f);
                } else {
                    log.warn("Could not decode image {}, copying raw bytes", sourceImagePath.getFileName());
                    try (InputStream rawStream = Files.newInputStream(sourceImagePath)) {
                        rawStream.transferTo(zipOut);
                    }
                }
            }
        }

        zipOut.closeArchiveEntry();
    }

    private void writeJpegImage(BufferedImage image, ZipArchiveOutputStream zipOut, float quality)
            throws IOException {
        BufferedImage rgbImage = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgbImage.getGraphics().drawImage(image, 0, 0, null);
            rgbImage.getGraphics().dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG image writer available");
        }
        ImageWriter writer = writers.next();

        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }

        zipOut.write(baos.toByteArray());
    }

    private String generatePageHtml(String imageFileName, int pageNumber) throws IOException, TemplateException {
        Map<String, Object> model = new HashMap<>();
        model.put("imageFileName", "../Images/" + imageFileName);
        model.put("pageNumber", pageNumber);
        model.put("stylesheetPath", "../Styles/stylesheet.css");

        return processTemplate("xml/image_page.xhtml.ftl", model);
    }

    private void addContentOpf(ZipArchiveOutputStream zipOut, BookEntity bookEntity,
            List<EpubContentFileGroup> contentGroups) throws IOException, TemplateException {

        Map<String, Object> model = createBookMetadataModel(bookEntity);

        List<EpubContentFileGroup> relativeContentGroups = contentGroups.stream()
                .map(group -> new EpubContentFileGroup(
                        group.contentKey(),
                        makeRelativeToOebps(group.imagePath()),
                        makeRelativeToOebps(group.htmlPath())))
                .toList();

        model.put("contentFileGroups", relativeContentGroups);
        model.put("coverImagePath", makeRelativeToOebps(COVER_IMAGE_PATH));
        model.put("tocNcxPath", makeRelativeToOebps(TOC_NCX_PATH));
        model.put("navXhtmlPath", makeRelativeToOebps(NAV_XHTML_PATH));
        model.put("stylesheetCssPath", makeRelativeToOebps(STYLESHEET_CSS_PATH));
        model.put("firstPageId", contentGroups.isEmpty() ? "" : "page_" + contentGroups.getFirst().contentKey());

        String contentOpf = processTemplate("xml/content.opf.ftl", model);

        ZipArchiveEntry contentEntry = new ZipArchiveEntry(CONTENT_OPF_PATH);
        zipOut.putArchiveEntry(contentEntry);
        zipOut.write(contentOpf.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private void addTocNcx(ZipArchiveOutputStream zipOut, BookEntity bookEntity,
            List<EpubContentFileGroup> contentGroups) throws IOException, TemplateException {

        Map<String, Object> model = createBookMetadataModel(bookEntity);
        model.put("contentFileGroups", contentGroups);

        String tocNcx = processTemplate("xml/toc.xml.ftl", model);

        ZipArchiveEntry tocEntry = new ZipArchiveEntry(TOC_NCX_PATH);
        zipOut.putArchiveEntry(tocEntry);
        zipOut.write(tocNcx.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private void addNavXhtml(ZipArchiveOutputStream zipOut, BookEntity bookEntity,
            List<EpubContentFileGroup> contentGroups) throws IOException, TemplateException {

        Map<String, Object> model = createBookMetadataModel(bookEntity);
        model.put("contentFileGroups", contentGroups);

        String navXhtml = processTemplate("xml/nav.xhtml.ftl", model);

        ZipArchiveEntry navEntry = new ZipArchiveEntry(NAV_XHTML_PATH);
        zipOut.putArchiveEntry(navEntry);
        zipOut.write(navXhtml.getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
    }

    private Map<String, Object> createBookMetadataModel(BookEntity bookEntity) {
        Map<String, Object> model = new HashMap<>();

        if (bookEntity != null && bookEntity.getMetadata() != null) {
            var metadata = bookEntity.getMetadata();

            model.put("title", metadata.getTitle() != null ? metadata.getTitle() : "Unknown Comic");
            model.put("language", metadata.getLanguage() != null ? metadata.getLanguage() : "en");

            if (metadata.getSubtitle() != null && !metadata.getSubtitle().trim().isEmpty()) {
                model.put("subtitle", metadata.getSubtitle());
            }
            if (metadata.getDescription() != null && !metadata.getDescription().trim().isEmpty()) {
                model.put("description", metadata.getDescription());
            }

            if (metadata.getSeriesName() != null && !metadata.getSeriesName().trim().isEmpty()) {
                model.put("seriesName", metadata.getSeriesName());
            }
            if (metadata.getSeriesNumber() != null) {
                model.put("seriesNumber", metadata.getSeriesNumber());
            }
            if (metadata.getSeriesTotal() != null) {
                model.put("seriesTotal", metadata.getSeriesTotal());
            }

            if (metadata.getPublisher() != null && !metadata.getPublisher().trim().isEmpty()) {
                model.put("publisher", metadata.getPublisher());
            }
            if (metadata.getPublishedDate() != null) {
                model.put("publishedDate", metadata.getPublishedDate().toString());
            }
            if (metadata.getPageCount() != null && metadata.getPageCount() > 0) {
                model.put("pageCount", metadata.getPageCount());
            }

            if (metadata.getIsbn13() != null && !metadata.getIsbn13().trim().isEmpty()) {
                model.put("isbn13", metadata.getIsbn13());
            }
            if (metadata.getIsbn10() != null && !metadata.getIsbn10().trim().isEmpty()) {
                model.put("isbn10", metadata.getIsbn10());
            }
            if (metadata.getAsin() != null && !metadata.getAsin().trim().isEmpty()) {
                model.put("asin", metadata.getAsin());
            }
            if (metadata.getGoodreadsId() != null && !metadata.getGoodreadsId().trim().isEmpty()) {
                model.put("goodreadsId", metadata.getGoodreadsId());
            }

            if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
                model.put("authors", metadata.getAuthors().stream()
                        .map(AuthorEntity::getName)
                        .toList());
            }

            if (metadata.getCategories() != null && !metadata.getCategories().isEmpty()) {
                model.put("categories", metadata.getCategories().stream()
                        .map(CategoryEntity::getName)
                        .toList());
            }

            if (metadata.getTags() != null && !metadata.getTags().isEmpty()) {
                model.put("tags", metadata.getTags().stream()
                        .map(TagEntity::getName)
                        .toList());
            }

            model.put("identifier", "urn:uuid:" + UUID.randomUUID());
        } else {
            model.put("title", "Unknown Comic");
            model.put("language", "en");
            model.put("identifier", "urn:uuid:" + UUID.randomUUID());
        }

        model.put("modified", Instant.now().toString());

        return model;
    }

    private String processTemplate(String templateName, Map<String, Object> model)
            throws IOException, TemplateException {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new IOException("Failed to load template: " + templateName, e);
        } catch (TemplateException e) {
            throw new TemplateException("Failed to process template: " + templateName, e, null);
        }
    }

    private String loadResourceAsString(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String makeRelativeToOebps(String fullPath) {
        Path oebpsPath = Paths.get("OEBPS");
        Path targetPath = Paths.get(fullPath);

        if (targetPath.startsWith(oebpsPath)) {
            return oebpsPath.relativize(targetPath).toString().replace('\\', '/');
        }

        return fullPath;
    }

    private long calculateCrc32(byte[] data) {
        java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
        crc32.update(data);
        return crc32.getValue();
    }

}
