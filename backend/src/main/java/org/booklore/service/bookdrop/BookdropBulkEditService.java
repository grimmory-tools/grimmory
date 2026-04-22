package org.booklore.service.bookdrop;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.BookdropBulkEditRequest;
import org.booklore.model.dto.response.BookdropBulkEditResult;
import org.booklore.model.entity.BookdropFileEntity;
import org.booklore.repository.BookdropFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookdropBulkEditService {

    private static final int BATCH_SIZE = 500;

    private final BookdropFileRepository bookdropFileRepository;
    private final BookdropMetadataHelper metadataHelper;

    @Transactional
    public BookdropBulkEditResult bulkEdit(BookdropBulkEditRequest request) {
        List<Long> fileIds = metadataHelper.resolveFileIds(
                request.isSelectAll(),
                request.getExcludedIds(),
                request.getSelectedIds()
        );
        
        return processBulkEditInBatches(fileIds, request);
    }

    private BookdropBulkEditResult processBulkEditInBatches(List<Long> fileIds, BookdropBulkEditRequest request) {
        int totalSuccessCount = 0;
        int totalFailedCount = 0;
        int totalFiles = fileIds.size();

        for (int batchStart = 0; batchStart < fileIds.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, fileIds.size());
            
            BatchEditResult batchResult = processSingleBatch(fileIds, batchStart, batchEnd, request);
            
            totalSuccessCount += batchResult.successCount();
            totalFailedCount += batchResult.failureCount();
            
            log.debug("Processed batch {}-{} of {}: {} successful, {} failed", 
                    batchStart, batchEnd, totalFiles, batchResult.successCount(), batchResult.failureCount());
        }

        return BookdropBulkEditResult.builder()
                .totalFiles(totalFiles)
                .successfullyUpdated(totalSuccessCount)
                .failed(totalFailedCount)
                .build();
    }

    private BatchEditResult processSingleBatch(List<Long> allFileIds, int batchStart, int batchEnd, 
                                                BookdropBulkEditRequest request) {
        List<Long> batchIds = allFileIds.subList(batchStart, batchEnd);
        List<BookdropFileEntity> batchFiles = bookdropFileRepository.findAllById(batchIds);
        
        int successCount = 0;
        int failureCount = 0;
        Set<Long> failedFileIds = new HashSet<>();

        for (BookdropFileEntity file : batchFiles) {
            try {
                updateFileMetadata(file, request);
                successCount++;
            } catch (RuntimeException e) {
                log.error("Failed to update metadata for file {} ({}): {}", 
                         file.getId(), file.getFileName(), e.getMessage(), e);
                failureCount++;
                failedFileIds.add(file.getId());
            }
        }

        List<BookdropFileEntity> filesToSave = batchFiles.stream()
                .filter(file -> !failedFileIds.contains(file.getId()))
                .toList();

        if (!filesToSave.isEmpty()) {
            bookdropFileRepository.saveAll(filesToSave);
        }
        
        return new BatchEditResult(successCount, failureCount);
    }

    private void updateFileMetadata(BookdropFileEntity file, BookdropBulkEditRequest request) {
        BookMetadata currentMetadata = metadataHelper.getCurrentMetadata(file);
        BookMetadata updates = request.getFields();
        Set<String> enabledFields = request.getEnabledFields();
        boolean mergeArrays = request.isMergeArrays();

        if (enabledFields.contains("seriesName") && updates.getSeriesName() != null) {
            currentMetadata.setSeriesName(updates.getSeriesName());
        }
        if (enabledFields.contains("seriesTotal") && updates.getSeriesTotal() != null) {
            currentMetadata.setSeriesTotal(updates.getSeriesTotal());
        }
        if (enabledFields.contains("publisher") && updates.getPublisher() != null) {
            currentMetadata.setPublisher(updates.getPublisher());
        }
        if (enabledFields.contains("language") && updates.getLanguage() != null) {
            currentMetadata.setLanguage(updates.getLanguage());
        }

        updateArrayField("authors", enabledFields, currentMetadata.getAuthors(), updates.getAuthors(), 
                currentMetadata::setAuthors, mergeArrays);
        updateArrayField("categories", enabledFields, currentMetadata.getCategories(), updates.getCategories(), 
                currentMetadata::setCategories, mergeArrays);
        updateArrayField("moods", enabledFields, currentMetadata.getMoods(), updates.getMoods(), 
                currentMetadata::setMoods, mergeArrays);
        updateArrayField("tags", enabledFields, currentMetadata.getTags(), updates.getTags(), 
                currentMetadata::setTags, mergeArrays);

        metadataHelper.updateFetchedMetadata(file, currentMetadata);
    }

    private void updateArrayField(String fieldName, Set<String> enabledFields,
                                  List<String> currentValue, List<String> newValue,
                                  java.util.function.Consumer<List<String>> setter, boolean mergeArrays) {
        if (enabledFields.contains(fieldName) && newValue != null) {
            if (mergeArrays && currentValue != null) {
                List<String> merged = new ArrayList<>(currentValue);
                for (String v : newValue) {
                    if (!merged.contains(v)) merged.add(v);
                }
                setter.accept(merged);
            } else {
                setter.accept(newValue);
            }
        }
    }

    private void updateArrayField(String fieldName, Set<String> enabledFields,
                                  Set<String> currentValue, Set<String> newValue,
                                  java.util.function.Consumer<Set<String>> setter, boolean mergeArrays) {
        if (enabledFields.contains(fieldName) && newValue != null) {
            if (mergeArrays && currentValue != null) {
                Set<String> merged = new LinkedHashSet<>(currentValue);
                merged.addAll(newValue);
                setter.accept(merged);
            } else {
                setter.accept(newValue);
            }
        }
    }

    private record BatchEditResult(int successCount, int failureCount) {}
}