package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboSpanPositionMap;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.KoboSpanMapEntity;
import org.booklore.repository.KoboSpanMapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KoboSpanMapService {

    private final KoboSpanMapRepository koboSpanMapRepository;
    private final KoboSpanMapExtractionService koboSpanMapExtractionService;

    @Transactional
    public void computeAndStoreIfNeeded(BookFileEntity bookFile, File kepubFile) throws java.io.IOException {
        if (bookFile == null || bookFile.getId() == null) {
            return;
        }
        String currentHash = bookFile.getCurrentHash();
        if (currentHash == null || currentHash.isBlank()) {
            log.debug("Skipping Kobo span map creation for file {} because current hash is missing", bookFile.getId());
            return;
        }

        Optional<KoboSpanMapEntity> existingMap = koboSpanMapRepository.findByBookFileId(bookFile.getId());
        if (existingMap.filter(map -> currentHash.equals(map.getFileHash()) && map.getSpanMap() != null).isPresent()) {
            return;
        }
        if (kepubFile == null || !kepubFile.isFile()) {
            return;
        }

        KoboSpanPositionMap spanMap = koboSpanMapExtractionService.extractFromKepub(kepubFile);
        KoboSpanMapEntity entity = existingMap.orElseGet(KoboSpanMapEntity::new);
        entity.setBookFile(bookFile);
        entity.setFileHash(currentHash);
        entity.setSpanMap(spanMap);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        koboSpanMapRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Map<Long, KoboSpanPositionMap> getValidMaps(Map<Long, BookFileEntity> bookFilesByFileId) {
        if (bookFilesByFileId.isEmpty()) {
            return Map.of();
        }
        Map<Long, KoboSpanPositionMap> result = new HashMap<>();
        for (KoboSpanMapEntity entity : koboSpanMapRepository.findByBookFileIdIn(bookFilesByFileId.keySet())) {
            Long fileId = entity.getBookFile().getId();
            BookFileEntity bookFile = bookFilesByFileId.get(fileId);
            if (bookFile != null && bookFile.getCurrentHash() != null
                    && bookFile.getCurrentHash().equals(entity.getFileHash())) {
                result.put(fileId, entity.getSpanMap());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<KoboSpanPositionMap> getValidMap(BookFileEntity bookFile) {
        if (bookFile == null || bookFile.getId() == null) {
            return Optional.empty();
        }
        String currentHash = bookFile.getCurrentHash();
        if (currentHash == null || currentHash.isBlank()) {
            return Optional.empty();
        }

        return koboSpanMapRepository.findByBookFileId(bookFile.getId())
                .filter(map -> currentHash.equals(map.getFileHash()))
                .map(KoboSpanMapEntity::getSpanMap);
    }
}
