package com.adityachandel.booklore.quartz;

import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class RefreshMetadataJob implements InterruptableJob {

    private final MetadataRefreshService metadataRefreshService;
    private volatile Thread executionThread;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        executionThread = Thread.currentThread();
        try {
            MetadataRefreshRequest request = (MetadataRefreshRequest) context.getMergedJobDataMap().get("request");
            Long userId = (Long) context.getMergedJobDataMap().get("userId");
            String jobId = (String) context.getMergedJobDataMap().get("jobId");
            log.info("Starting metadata refresh job with ID: {}", jobId);
            metadataRefreshService.refreshMetadata(request, userId, jobId);
            log.info("Completed metadata refresh job with ID: {}", jobId);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException) {
                log.info("Metadata refresh job with ID was interrupted: {}", context.getMergedJobDataMap().get("jobId"));
                Thread.currentThread().interrupt();
                return;
            }
            throw new JobExecutionException("Error occurred while executing metadata refresh job", e);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Metadata refresh job was interrupted");
                Thread.currentThread().interrupt();
                return;
            }
            throw new JobExecutionException("Error occurred while executing metadata refresh job", e);
        } finally {
            executionThread = null;
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        Thread thread = executionThread;
        if (thread != null) {
            thread.interrupt();
        } else {
            log.warn("No execution thread found to interrupt");
        }
    }
}
