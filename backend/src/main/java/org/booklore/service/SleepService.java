package org.booklore.service;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SleepService {
    public void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }

    public void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
