package org.booklore.service.upload.lcp;

import lombok.RequiredArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.service.appsettings.AppSettingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LcpImportService {
    private final LcpEpubConverter converter;
    private final LcpPublicationDownloader downloader;
    private final LcpSettingsService settings;
    private final AppSettingService appSettings;

    public PreparedUpload prepare(MultipartFile file, String passphrase) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".lcpl")) return new PreparedUpload(file, null);
        if (file.getSize() > LcpEpubConverter.MAX_LICENSE_BYTES) throw ApiError.INVALID_INPUT.createException("LCP license exceeds the size limit.");
        byte[] data;
        try (InputStream stream = file.getInputStream()) { data = LcpEpubConverter.readBounded(stream, LcpEpubConverter.MAX_LICENSE_BYTES); }
        var license = converter.readLicense(data);
        byte[] userKey = passphrase != null && !passphrase.isEmpty() ? LcpEpubConverter.deriveUserKey(passphrase)
                : settings.getDefaultUserKey().orElseThrow(ApiError.LCP_PASSPHRASE_REQUIRED::createException);
        byte[] contentKey;
        try { contentKey = converter.unlock(license, userKey); }
        finally { Arrays.fill(userKey, (byte) 0); }
        Path downloaded = null;
        Path converted = null;
        boolean prepared = false;
        try {
            long limit = Math.min(512L, appSettings.getAppSettings().getMaxFileUploadSizeInMb()) * 1024 * 1024;
            downloaded = Files.createTempFile("lcp-download-", ".epub");
            converted = Files.createTempFile("lcp-converted-", ".epub");
            downloader.download(license.publication(), downloaded, limit);
            converter.convert(downloaded, converted, contentKey, limit);
            String baseName = StringUtils.getFilename(StringUtils.cleanPath(name));
            if (baseName == null) throw ApiError.INVALID_INPUT.createException("Invalid LCP filename.");
            String epubName = baseName.substring(0, baseName.length() - 5) + ".epub";
            PreparedUpload result = new PreparedUpload(new PathMultipartFile(converted, epubName, file.getName()), converted);
            prepared = true;
            return result;
        } finally {
            Arrays.fill(contentKey, (byte) 0);
            if (downloaded != null) Files.deleteIfExists(downloaded);
            if (!prepared && converted != null) Files.deleteIfExists(converted);
        }
    }

    public record PreparedUpload(MultipartFile file, Path temporary) implements AutoCloseable {
        @Override
        public void close() throws IOException { if (temporary != null) Files.deleteIfExists(temporary); }
    }

    private record PathMultipartFile(Path path, String originalFilename, String name) implements MultipartFile {
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return "application/epub+zip"; }
        @Override public boolean isEmpty() { return getSize() == 0; }
        @Override public long getSize() { try { return Files.size(path); } catch (IOException exception) { throw ApiError.FILE_READ_ERROR.createException("Converted EPUB is unavailable"); } }
        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
        @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
        @Override public void transferTo(File destination) throws IOException { transferTo(destination.toPath()); }
        @Override public void transferTo(Path destination) throws IOException { Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING); }
    }
}
