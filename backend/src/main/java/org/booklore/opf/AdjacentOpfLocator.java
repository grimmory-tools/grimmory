package org.booklore.opf;

import org.booklore.model.dto.settings.LibraryFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class AdjacentOpfLocator {

    public Optional<Path> find(LibraryFile libraryFile) {
        if (libraryFile == null || libraryFile.getFullPath() == null) {
            return Optional.empty();
        }

        Path bookPath = libraryFile.getFullPath().normalize();
        Path folder = libraryFile.isFolderBased() ? bookPath : bookPath.getParent();
        if (folder == null || !Files.isDirectory(folder)) {
            return Optional.empty();
        }
        folder = folder.normalize();

        String stem = libraryFile.isFolderBased()
                ? folder.getFileName().toString()
                : stripExtension(bookPath.getFileName().toString());
        String folderName = folder.getFileName() != null ? folder.getFileName().toString() : "";

        Optional<Path> explicit = firstExistingInside(folder,
                folder.resolve(stem + ".opf"),
                folder.resolve(folderName + ".opf"),
                folder.resolve("metadata.opf"));
        if (explicit.isPresent()) {
            return explicit;
        }

        return findSingleUnambiguousOpf(folder);
    }

    Optional<Path> findSingleUnambiguousOpf(Path folder) {
        if (hasMultipleBookFiles(folder)) {
            return Optional.empty();
        }

        try (Stream<Path> stream = Files.list(folder)) {
            var opfs = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".opf"))
                    .map(Path::normalize)
                    .filter(path -> isInside(folder, path))
                    .limit(2)
                    .toList();
            return opfs.size() == 1 ? Optional.of(opfs.get(0)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    boolean isInside(Path folder, Path candidate) {
        return candidate.normalize().startsWith(folder.normalize());
    }

    private Optional<Path> firstExistingInside(Path folder, Path... candidates) {
        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (isInside(folder, normalized) && Files.isRegularFile(normalized)) {
                return Optional.of(normalized);
            }
        }
        return Optional.empty();
    }

    private boolean hasMultipleBookFiles(Path folder) {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isBookLike(path.getFileName().toString()))
                    .limit(2)
                    .count() > 1;
        } catch (IOException e) {
            return true;
        }
    }

    private boolean isBookLike(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".epub")
                || lower.endsWith(".pdf")
                || lower.endsWith(".azw3")
                || lower.endsWith(".mobi")
                || lower.endsWith(".fb2")
                || lower.endsWith(".cbz")
                || lower.endsWith(".cbr")
                || lower.endsWith(".cb7");
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
