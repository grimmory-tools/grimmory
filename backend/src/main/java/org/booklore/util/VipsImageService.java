package org.booklore.util;

import app.photofox.vipsffm.VBlob;
import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.VSource;
import app.photofox.vipsffm.VTarget;
import app.photofox.vipsffm.VipsError;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsBandFormat;
import app.photofox.vipsffm.enums.VipsInterpretation;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.pdfium4j.PdfPage;
import org.springframework.stereotype.Service;
import org.booklore.exception.ApiError;
import org.booklore.exception.APIException;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.booklore.nativelib.NativeLibraryManager;

@Slf4j
@Service
public class VipsImageService {
    private final NativeLibraryManager nativeLibraryManager;

    private static final String OPTION_STRIP = "strip";
    private static final String OPTION_HEIGHT = "height";
    private static final String OPTION_QUALITY = "Q";
    private static final String OPTION_OPTIMIZE_CODING = "optimize_coding";

    /**
     * PDFium flag: reverse byte order so the rasteriser writes RGBA instead of its native BGRA.
     * Value matches FPDF_REVERSE_BYTE_ORDER = 0x10 in PDFium's fpdfview.h.
     */
    private static final int FPDF_REVERSE_BYTE_ORDER = 0x10;

    public VipsImageService(NativeLibraryManager nativeLibraryManager) {
        this.nativeLibraryManager = nativeLibraryManager;
    }

    public ImageDimensions readDimensions(byte[] data) {
        return runWithArena(arena -> {
            VImage img = VImage.newFromBytes(arena, data).autorot();
            return new ImageDimensions(img.getWidth(), img.getHeight());
        });
    }

    public ImageDimensions readDimensions(InputStream is) {
        return runWithArena(arena -> {
            VImage img = VImage.newFromSource(arena, VSource.newFromInputStream(arena, is)).autorot();
            return new ImageDimensions(img.getWidth(), img.getHeight());
        });
    }

    public ImageDimensions readDimensionsFromFile(Path path) {
        return runWithArena(arena -> {
            VImage img = VImage.newFromFile(arena, path.toString()).autorot();
            return new ImageDimensions(img.getWidth(), img.getHeight());
        });
    }

    public boolean canDecode(byte[] data) {
        try {
            return readDimensions(data) != null;
        } catch (Exception _) {
            return false;
        }
    }

    public boolean canDecode(Path path) {
        try {
            return readDimensionsFromFile(path) != null;
        } catch (Exception _) {
            return false;
        }
    }

    public boolean canDecode(InputStream is) {
        try {
            return readDimensions(is) != null;
        } catch (Exception _) {
            return false;
        }
    }

    public void flattenResizeAndSave(byte[] data, Path out, int maxW, int maxH) {
        runWithArena(arena -> {
            VBlob blob = VBlob.newFromBytes(arena, data);
            VImage img = VImage.thumbnailBuffer(arena, blob, maxW, VipsOption.Int(OPTION_HEIGHT, maxH));
            img = img.colourspace(VipsInterpretation.INTERPRETATION_sRGB);
            img = flattenIfHasAlpha(img);
            img.jpegsave(out.toString(), VipsOption.Int(OPTION_QUALITY, 85), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));
            return null;
        });
    }

    public void flattenResizeAndSave(Path in, Path out, int maxW, int maxH) {
        runWithArena(arena -> {
            VImage img = VImage.thumbnail(arena, in.toString(), maxW, VipsOption.Int(OPTION_HEIGHT, maxH));
            img = img.colourspace(VipsInterpretation.INTERPRETATION_sRGB);
            img = flattenIfHasAlpha(img);
            img.jpegsave(out.toString(), VipsOption.Int(OPTION_QUALITY, 85), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));
            return null;
        });
    }

    public void processStreamToJpeg(InputStream is, OutputStream os, int maxW, int maxH) {
        runWithArena(arena -> {
            VSource source = VSource.newFromInputStream(arena, is);
            VImage img = VImage.thumbnailSource(arena, source, maxW, VipsOption.Int(OPTION_HEIGHT, maxH));
            img = img.colourspace(VipsInterpretation.INTERPRETATION_sRGB);
            img = flattenIfHasAlpha(img);
            img.jpegsaveTarget(VTarget.newFromOutputStream(arena, os), VipsOption.Int(OPTION_QUALITY, 85), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));
            return null;
        });
    }

    /**
     * Unified cover processing that handles loading, cropping, and thumbnailing in a single native pipeline.
     * This avoids multiple I/O passes.
     */
    public ImageDimensions processCoverUnified(InputStream is, Path coverOut, Path thumbOut,
                                             int maxW, int maxH, int thumbW, int thumbH,
                                             boolean verticalCrop, boolean horizontalCrop,
                                             double threshold, boolean smartCrop,
                                             double targetAspectRatio, double smartCropMargin) {
        return runWithArena(arena -> {
            VSource source = VSource.newFromInputStream(arena, is);
            return processCoverPipeline(VImage.newFromSource(arena, source), coverOut, thumbOut,
                    maxW, maxH, thumbW, thumbH, verticalCrop, horizontalCrop, threshold, smartCrop, 
                    targetAspectRatio, smartCropMargin);
        });
    }

    public ImageDimensions processCoverUnified(Path in, Path coverOut, Path thumbOut,
                                             int maxW, int maxH, int thumbW, int thumbH,
                                             boolean verticalCrop, boolean horizontalCrop,
                                             double threshold, boolean smartCrop,
                                             double targetAspectRatio, double smartCropMargin) {
        return runWithArena(arena -> processCoverPipeline(VImage.newFromFile(arena, in.toString()), coverOut, thumbOut,
                maxW, maxH, thumbW, thumbH, verticalCrop, horizontalCrop, threshold, smartCrop,
                targetAspectRatio, smartCropMargin));
    }

    public ImageDimensions processPdfCoverUnified(PdfPage page, Path coverOut, Path thumbOut,
                                                int maxW, int maxH, int thumbW, int thumbH,
                                                boolean verticalCrop, boolean horizontalCrop,
                                                double threshold, boolean smartCrop,
                                                double targetAspectRatio, double smartCropMargin) {
        return runWithArena(arena -> {
            VImage img = renderPdfPageAsVipsImage(arena, page, 150); // Use 150 DPI for covers
            return processCoverPipeline(img, coverOut, thumbOut,
                    maxW, maxH, thumbW, thumbH, verticalCrop, horizontalCrop, threshold, smartCrop,
                    targetAspectRatio, smartCropMargin);
        });
    }

    private static ImageDimensions processCoverPipeline(VImage img, Path coverOut, Path thumbOut,
                                                        int maxW, int maxH, int thumbW, int thumbH,
                                                        boolean verticalCrop, boolean horizontalCrop,
                                                        double threshold, boolean smartCrop,
                                                        double targetAspectRatio, double smartCropMargin) {
        if (targetAspectRatio <= 0) {
            throw ApiError.INVALID_INPUT.createException("targetAspectRatio must be > 0");
        }

        VImage croppedImage = img;
        croppedImage = croppedImage.autorot().colourspace(VipsInterpretation.INTERPRETATION_sRGB);

        int width = croppedImage.getWidth();
        int height = croppedImage.getHeight();
        double heightToWidthRatio = (double) height / width;
        double widthToHeightRatio = (double) width / height;

        // Perform cropping if needed
        if (verticalCrop && heightToWidthRatio > threshold) {
            int croppedHeight = (int) (width * targetAspectRatio);
            croppedHeight = Math.clamp(croppedHeight, 1, height);
            int startY = 0;
            if (smartCrop) {
                var output = croppedImage.findTrim(VipsOption.Int("threshold", 10));
                int margin = (int) (croppedHeight * smartCropMargin);
                startY = Math.max(0, output.top() - margin);
                startY = Math.min(startY, height - croppedHeight);
            }
            croppedImage = croppedImage.extractArea(0, startY, width, croppedHeight);
        } else if (horizontalCrop && widthToHeightRatio > threshold) {
            int croppedWidth = (int) (height / targetAspectRatio);
            croppedWidth = Math.clamp(croppedWidth, 1, width);
            int startX = (width - croppedWidth) / 2; // Center crop for horizontal
            if (smartCrop) {
                var output = croppedImage.findTrim(VipsOption.Int("threshold", 10));
                int margin = (int) (croppedWidth * smartCropMargin);
                startX = Math.max(0, output.left() - margin);
                startX = Math.min(startX, width - croppedWidth);
            }
            croppedImage = croppedImage.extractArea(startX, 0, croppedWidth, height);
        }

        // Flatten if needed before scaling
        croppedImage = flattenIfHasAlpha(croppedImage);

        // Resize to cover size
        VImage coverImg = croppedImage;
        if (croppedImage.getWidth() > maxW || croppedImage.getHeight() > maxH) {
            double scale = Math.min((double) maxW / croppedImage.getWidth(), (double) maxH / croppedImage.getHeight());
            coverImg = croppedImage.resize(scale);
        }

        coverImg.jpegsave(coverOut.toString(), VipsOption.Int(OPTION_QUALITY, 85), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));

        double thumbScale = Math.min((double) thumbW / coverImg.getWidth(), (double) thumbH / coverImg.getHeight());
        VImage thumbImg = coverImg.resize(thumbScale);
        thumbImg.jpegsave(thumbOut.toString(), VipsOption.Int(OPTION_QUALITY, 80), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));

        return new ImageDimensions(coverImg.getWidth(), coverImg.getHeight());
    }

    public ImageDimensions processAudiobookCoverUnified(Path in, Path coverOut, Path thumbOut,
                                                      int maxSquareSize, int thumbSize) {
        return runWithArena(arena -> {
            VImage img = VImage.newFromFile(arena, in.toString()).autorot();
            img = img.colourspace(VipsInterpretation.INTERPRETATION_sRGB);

            int width = img.getWidth();
            int height = img.getHeight();
            int size = Math.min(width, height);
            int cropX = (width - size) / 2;
            int cropY = (height - size) / 2;

            // Center-square crop
            img = img.extractArea(cropX, cropY, size, size);
            img = flattenIfHasAlpha(img);

            // Resize to audiobook cover size (maxSquareSize)
            int coverSize = Math.min(size, maxSquareSize);
            VImage coverImg = img.resize((double) coverSize / size);

            coverImg.jpegsave(coverOut.toString(), VipsOption.Int(OPTION_QUALITY, 85), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));

            VImage thumbImg = coverImg.resize((double) thumbSize / coverSize);
            thumbImg.jpegsave(thumbOut.toString(), VipsOption.Int(OPTION_QUALITY, 80), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));

            return new ImageDimensions(coverImg.getWidth(), coverImg.getHeight());
        });
    }

    public ImageDimensions processPhotoUnified(Path in, Path photoOut, Path thumbOut,
                                              int maxW, int maxH, int thumbW, int thumbH) {
        return runWithArena(arena -> {
            VImage img = VImage.newFromFile(arena, in.toString()).autorot();
            return processPhotoPipeline(img, photoOut, thumbOut, maxW, maxH, thumbW, thumbH);
        });
    }

    public ImageDimensions processPhotoUnified(InputStream is, Path photoOut, Path thumbOut,
                                              int maxW, int maxH, int thumbW, int thumbH) {
        return runWithArena(arena -> {
            VSource source = VSource.newFromInputStream(arena, is);
            VImage img = VImage.newFromSource(arena, source).autorot();
            return processPhotoPipeline(img, photoOut, thumbOut, maxW, maxH, thumbW, thumbH);
        });
    }

    private static ImageDimensions processPhotoPipeline(VImage img, Path photoOut, Path thumbOut,
                                                        int maxW, int maxH, int thumbW, int thumbH) {
        VImage colorSpaceImage = img.colourspace(VipsInterpretation.INTERPRETATION_sRGB);

        // Resize to original photo size (maxW, maxH)
        VImage photoImg = colorSpaceImage;
        if (colorSpaceImage.getWidth() > maxW || colorSpaceImage.getHeight() > maxH) {
            double scale = Math.min((double) maxW / colorSpaceImage.getWidth(), (double) maxH / colorSpaceImage.getHeight());
            photoImg = colorSpaceImage.resize(scale);
        }
        photoImg = flattenIfHasAlpha(photoImg);
        photoImg.jpegsave(photoOut.toString(), VipsOption.Int(OPTION_QUALITY, 85), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));

        double targetRatio = (double) thumbW / thumbH;
        int width = photoImg.getWidth();
        int height = photoImg.getHeight();
        double sourceRatio = (double) width / height;

        int cropW, cropH, cropX, cropY;
        if (sourceRatio > targetRatio) {
            cropH = height;
            cropW = (int) (cropH * targetRatio);
            cropX = (width - cropW) / 2;
            cropY = 0;
        } else {
            cropW = width;
            cropH = (int) (cropW / targetRatio);
            cropX = 0;
            cropY = (height - cropH) / 2;
        }

        cropW = Math.clamp(cropW, 1, width);
        cropH = Math.clamp(cropH, 1, height);
        cropX = Math.clamp(cropX, 0, width - cropW);
        cropY = Math.clamp(cropY, 0, height - cropH);

        VImage thumbImg = photoImg.extractArea(cropX, cropY, cropW, cropH);
        thumbImg = thumbImg.resize((double) thumbW / cropW);
        thumbImg.jpegsave(thumbOut.toString(), VipsOption.Int(OPTION_QUALITY, 80), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));

        return new ImageDimensions(photoImg.getWidth(), photoImg.getHeight());
    }

    public void transcodeStreamToJpeg(InputStream is, OutputStream os, int quality) {
        runWithArena(arena -> {
            VImage img = VImage.newFromSource(arena, VSource.newFromInputStream(arena, is)).autorot();
            img = img.colourspace(VipsInterpretation.INTERPRETATION_sRGB);
            img = flattenIfHasAlpha(img);
            img.jpegsaveTarget(VTarget.newFromOutputStream(arena, os), VipsOption.Int(OPTION_QUALITY, quality), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true));
            return null;
        });
    }

    public byte[] encodeAsPng(byte[] data) {
        return runWithArena(arena -> VImage.newFromBytes(arena, data).autorot().pngsaveBuffer().getBytes());
    }

    public byte[] downscaleAndEncodeJpeg(BufferedImage img, int targetW, int targetH, int q) {
        return runWithArena(arena -> {
            VImage vimg = bufferedImageToVips(arena, img);
            // We delegate downscaling to libvips.thumbnailImage even for BufferedImage inputs.
            if (vimg.getWidth() != targetW || vimg.getHeight() != targetH) {
                vimg = vimg.thumbnailImage(targetW, VipsOption.Int(OPTION_HEIGHT, targetH));
            }
            return vimg.jpegsaveBuffer(VipsOption.Int(OPTION_QUALITY, q), VipsOption.Boolean(OPTION_STRIP, true), VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true)).getBytes();
        });
    }

    private static final long MAX_RASTER_BYTES = 512L * 1024 * 1024; // 512MB
    private static final int VIPS_MAX_COORD = 10_000_000; // libvips default limit

    /**
     * Guards against excessive memory allocation and "pixel bombs".
     * Checks dimensions against individual coordinate limits and total byte size.
     */
    private static long checkedRasterBytes(int width, int height, int bands) {
        if (width <= 0 || width > VIPS_MAX_COORD || height <= 0 || height > VIPS_MAX_COORD) {
            throw ApiError.INVALID_INPUT.createException(
                    String.format("Invalid raster dimensions: %dx%d (limit: %d)", width, height, VIPS_MAX_COORD));
        }
        if (bands <= 0 || bands > 4) {
            throw ApiError.INVALID_INPUT.createException("Invalid number of bands: " + bands);
        }

        try {
            long total = Math.multiplyExact(Math.multiplyExact(width, (long) height), (long) bands);
            if (total > MAX_RASTER_BYTES) {
                throw ApiError.FILE_TOO_LARGE.createException(MAX_RASTER_BYTES / (1024 * 1024));
            }
            return total;
        } catch (ArithmeticException e) {
            throw ApiError.FILE_TOO_LARGE.createException("Raster size overflow");
        }
    }

    private static VImage bufferedImageToVips(Arena arena, BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();

        // Use direct transfer for compatible image types
        if (img.getType() == BufferedImage.TYPE_INT_RGB || img.getType() == BufferedImage.TYPE_INT_ARGB) {
            return fastBufferedImageToVips(arena, img);
        }

        // Fallback for other types: draw into a compatible buffer first
        BufferedImage compatible = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = compatible.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return fastBufferedImageToVips(arena, compatible);
    }

    private static VImage fastBufferedImageToVips(Arena arena, BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        // checkedRasterBytes ensures dimensions are safe
        checkedRasterBytes(w, h, 4); // treat as 4-band BGRX/BGRA

        // Access the underlying integer array directly
        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

        // Allocate off-heap and copy in one native call
        MemorySegment segment = arena.allocate((long) data.length * 4);
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_INT, 0, data.length);

        // TYPE_INT_RGB/ARGB in Java is 0xRRGGBB / 0xAARRGGBB.
        VImage vimg = VImage.newFromMemory(arena, segment, w, h, 4, VipsBandFormat.FORMAT_UCHAR.getRawValue());

        // Reorder BGR(A/X) to RGB and discard alpha/padding
        return vimg.extractBand(2)               // Red
                .bandjoin(arena, List.of(vimg.extractBand(1), vimg.extractBand(0))) // Green, Blue
                .copy(VipsOption.Enum("interpretation", VipsInterpretation.INTERPRETATION_sRGB));
    }

    private static VImage flattenIfHasAlpha(VImage img) throws VipsError {
        return img.hasAlpha() ? img.flatten() : img;
    }


    public byte[] renderPageToJpeg(PdfPage page, int dpi, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        renderPageToJpeg(page, dpi, quality, baos);
        return baos.toByteArray();
    }

    public byte[] renderPdfPageToJpeg(Path path, int page, int dpi, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128 * 1024);
        renderPdfPageToJpeg(path, page, dpi, quality, baos);
        return baos.toByteArray();
    }

    public void renderPdfPageToJpeg(Path path, int page, int dpi, int quality, OutputStream os) {
        runWithArena(arena -> {
            // Using VIPS native pdfload via path specifier is much faster than
            // manual rasterization as it allows for internal streaming and optimizations.
            String vipsPath = path.toAbsolutePath() + "[page=" + page + ",dpi=" + dpi + "]";
            VImage img = VImage.newFromFile(arena, vipsPath);
            img = img.colourspace(VipsInterpretation.INTERPRETATION_sRGB);
            img = flattenIfHasAlpha(img);
            img.jpegsaveTarget(
                    VTarget.newFromOutputStream(arena, os),
                    VipsOption.Int(OPTION_QUALITY, quality),
                    VipsOption.Boolean(OPTION_STRIP, true),
                    VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true)
            );
            return null;
        });
    }

    public void renderPageToJpeg(PdfPage page, int dpi, int quality, OutputStream outputStream) {
        runWithArena(arena -> {
            VImage vimg = flattenIfHasAlpha(renderPdfPageAsVipsImage(arena, page, dpi));
            vimg.jpegsaveTarget(
                    VTarget.newFromOutputStream(arena, outputStream),
                    VipsOption.Int(OPTION_QUALITY, quality),
                    VipsOption.Boolean(OPTION_STRIP, true),
                    VipsOption.Boolean(OPTION_OPTIMIZE_CODING, true)
            );
            return null;
        });
    }

    private static VImage renderPdfPageAsVipsImage(Arena arena, PdfPage page, int dpi) {
        var size = page.size();
        int w = size.widthPixels(dpi);
        int h = size.heightPixels(dpi);
        long strideLong = Math.multiplyExact(w, 4L);
        long totalBytes = checkedRasterBytes(w, h, 4);
        MemorySegment segment = arena.allocate(totalBytes);
        int stride = Math.toIntExact(strideLong);
        // FPDF_REVERSE_BYTE_ORDER (0x10) makes PDFium write RGBA instead of its native BGRA.
        // No band reordering needed, just tell vips the 4-band buffer is sRGB+alpha.
        page.renderTo(segment, w, h, stride, FPDF_REVERSE_BYTE_ORDER, 0xFFFFFFFF);
        return VImage.newFromMemory(arena, segment, w, h, 4, VipsBandFormat.FORMAT_UCHAR.getRawValue())
            .copy(VipsOption.Enum("interpretation", VipsInterpretation.INTERPRETATION_sRGB));
    }


    private <T> T runWithArena(VipsCallable<T> callable) {
        ensureAvailable();
        try (Arena arena = Arena.ofConfined()) {
            return callable.call(arena);
        } catch (VipsError e) {
            throw ApiError.FILE_READ_ERROR.createException("libvips error: " + e.getMessage());
        } catch (APIException e) {
            throw e;
        } catch (Exception e) {
            throw ApiError.FILE_READ_ERROR.createException("Image processing failed: " + e.getMessage());
        }
    }

    private void ensureAvailable() {
        if (!nativeLibraryManager.isVipsAvailable()) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("libvips is unavailable (degraded mode)");
        }
    }

    @FunctionalInterface
    private interface VipsCallable<T> {
        T call(Arena arena) throws Exception;
    }
}
