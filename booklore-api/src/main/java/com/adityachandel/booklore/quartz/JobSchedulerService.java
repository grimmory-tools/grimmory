package com.adityachandel.booklore.quartz;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.KeyMatcher;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSchedulerService {

    private final Scheduler scheduler;
    private final AuthenticationService authenticationService;

    private final BlockingQueue<RefreshJobWrapper> jobQueue = new LinkedBlockingQueue<>();
    private final Map<String, String> runningJobs = new ConcurrentHashMap<>();
    private boolean isJobRunning = false;

    public synchronized void scheduleMetadataRefresh(MetadataRefreshRequest request, Long userId) {
        log.info("Received request to schedule metadata refresh: {}", request);
        jobQueue.offer(new RefreshJobWrapper(request, userId));
        log.debug("Added request to job queue. Queue size: {}", jobQueue.size());
        processQueue();
    }

    private synchronized void processQueue() {
        if (isJobRunning) {
            log.debug("A job is already running. Queue processing is paused.");
            return;
        }
        if (jobQueue.isEmpty()) {
            log.debug("Job queue is empty. Nothing to process.");
            return;
        }

        isJobRunning = true;
        RefreshJobWrapper wrapper = jobQueue.poll();
        if (wrapper != null) {
            MetadataRefreshRequest request = wrapper.getRequest();
            Long userId = wrapper.getUserId();
            log.info("Processing job from queue. Remaining queue size: {}", jobQueue.size());
            String jobId = generateUniqueJobId(request);
            try {
                scheduleJob(request, userId, jobId);
            } catch (Exception e) {
                isJobRunning = false;
                log.error("Failed to schedule job with ID: {}. Error: {}", jobId, e.getMessage(), e);
                throw e;
            }
        }
    }

    public synchronized boolean cancelJob(String jobId) {
        try {
            String quartzJobId = runningJobs.get(jobId);
            if (quartzJobId == null) {
                log.warn("Job with ID {} not found in running jobs", jobId);
                return false;
            }

            JobKey jobKey = new JobKey(quartzJobId, "metadataRefreshJobGroup");
            boolean cancelled = scheduler.interrupt(jobKey);

            if (cancelled) {
                runningJobs.remove(jobId);
                isJobRunning = false;
                log.info("Job {} cancellation scheduled", jobId);
                processQueue();
            } else {
                log.warn("Failed to cancel job {}", jobId);
            }

            return cancelled;
        } catch (UnableToInterruptJobException e) {
            log.error("Job {} cannot be interrupted: {}", jobId, e.getMessage());
            return false;
        }
    }

    private void scheduleJob(MetadataRefreshRequest request, Long userId, String jobId) {
        try {
            BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("request", request);
            jobDataMap.put("userId", userId);
            jobDataMap.put("jobId", jobId);
            jobDataMap.put("user", authenticatedUser);

            JobDetail jobDetail = JobBuilder.newJob(RefreshMetadataJob.class)
                    .withIdentity(jobId, "metadataRefreshJobGroup")
                    .usingJobData(jobDataMap)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(jobId, "metadataRefreshJobGroup")
                    .startNow()
                    .build();

            runningJobs.put(jobId, jobId);

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Job scheduled successfully with ID: {}", jobId);

            scheduler.getListenerManager().addJobListener(new JobListener() {
                @Override
                public String getName() {
                    return "JobCompletionListener";
                }

                @Override
                public void jobToBeExecuted(JobExecutionContext context) {
                    log.debug("Job is about to be executed. JobKey: {}", context.getJobDetail().getKey());
                }

                @Override
                public void jobExecutionVetoed(JobExecutionContext context) {
                    log.warn("Job execution was vetoed. JobKey: {}", context.getJobDetail().getKey());
                }

                @Override
                public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
                    log.info("Job executed. JobKey: {}", context.getJobDetail().getKey());
                    if (jobException != null) {
                        log.error("Job execution encountered an error: {}", jobException.getMessage(), jobException);
                    }
                    runningJobs.remove(jobId);
                    isJobRunning = false;
                    log.debug("Job completion handled. Processing next job in the queue.");
                    processQueue();
                }
            }, KeyMatcher.keyEquals(jobDetail.getKey()));

        } catch (SchedulerException e) {
            log.error("Error while scheduling job with ID: {}. Error: {}", jobId, e.getMessage(), e);
            throw ApiError.SCHEDULE_REFRESH_ERROR.createException(e.getMessage());
        }
    }

    private String generateUniqueJobId(MetadataRefreshRequest request) {
        String jobId = "metadataRefreshJob_" + System.currentTimeMillis();
        log.debug("Generated unique job ID: {}", jobId);
        return jobId;
    }

    @Getter
    private static class RefreshJobWrapper {
        private final MetadataRefreshRequest request;
        private final Long userId;

        public RefreshJobWrapper(MetadataRefreshRequest request, Long userId) {
            this.request = request;
            this.userId = userId;
        }
    }
}