package org.grimmory.service.reader;

import org.grimmory.exception.ApiError;
import org.grimmory.model.dto.response.AudiobookInfo;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookFileEntity;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.repository.BookRepository;
import org.grimmory.service.FileStreamingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Service for audiobook reader operations.
 * Orchestrates metadata extraction, file access, and streaming.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudiobookReaderService {

    private final BookRepository bookRepository;
    private final AudioMetadataService audioMetadataService;
    private final AudioFileUtilityService audioFileUtility;
    private final FileStreamingService fileStreamingService;

    /**
     * Get audiobook information including metadata, chapters, and tracks.
     */
    @Transactional
    public AudiobookInfo getAudiobookInfo(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.getFullFilePath();

        try {
            return audioMetadataService.getMetadata(bookFile, audioPath);
        } catch (Exception e) {
            log.error("Failed to read audiobook metadata for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read audiobook: " + e.getMessage());
        }
    }

    /**
     * Get the path to an audio file for streaming.
     * For folder-based audiobooks, returns the path to a specific track.
     */
    @Transactional(readOnly = true)
    public Path getAudioFilePath(Long bookId, String bookType, Integer trackIndex) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);

        if (bookFile.isFolderBased()) {
            if (trackIndex == null) {
                trackIndex = 0;
            }
            List<Path> tracks = audioFileUtility.listAudioFiles(bookFile.getFullFilePath());
            if (trackIndex < 0 || trackIndex >= tracks.size()) {
                throw ApiError.FILE_NOT_FOUND.createException("Track index out of range: " + trackIndex);
            }
            return tracks.get(trackIndex);
        } else {
            return bookFile.getFullFilePath();
        }
    }

    /**
     * Stream an audio file with HTTP Range support for seeking.
     */
    public void streamWithRangeSupport(Path filePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contentType = audioFileUtility.getContentType(filePath);
        fileStreamingService.streamWithRangeSupport(filePath, contentType, request, response);
    }

    /**
     * Get embedded cover art from an audiobook file.
     */
    @Transactional(readOnly = true)
    public byte[] getEmbeddedCoverArt(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.isFolderBased() ? bookFile.getFirstAudioFile() : bookFile.getFullFilePath();
        return audioMetadataService.getEmbeddedCoverArt(audioPath);
    }

    /**
     * Get the MIME type of embedded cover art.
     */
    @Transactional(readOnly = true)
    public String getCoverArtMimeType(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.isFolderBased() ? bookFile.getFirstAudioFile() : bookFile.getFullFilePath();
        return audioMetadataService.getCoverArtMimeType(audioPath);
    }

    /**
     * Get the content type for an audio file.
     */
    public String getContentType(Path audioPath) {
        return audioFileUtility.getContentType(audioPath);
    }

    @Transactional(readOnly = true)
    protected BookFileEntity getAudiobookFile(Long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findByIdForAudiobook(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (bookType != null) {
            BookFileType requestedType = BookFileType.valueOf(bookType.toUpperCase());
            return bookEntity.getBookFiles().stream()
                    .filter(bf -> bf.getBookType() == requestedType)
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No file of type " + bookType + " found for book"));
        }

        return bookEntity.getBookFiles().stream()
                .filter(bf -> bf.getBookType() == BookFileType.AUDIOBOOK && bf.isBookFormat())
                .findFirst()
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No audiobook file found for book " + bookId));
    }
}
