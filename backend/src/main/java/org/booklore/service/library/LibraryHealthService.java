package org.booklore.service.library;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.websocket.LibraryHealthPayload;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.LibraryPathRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryHealthService {

    private final LibraryPathRepository libraryPathRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConcurrentHashMap<Long, Boolean> previousHealth = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        var health = checkHealth();
        previousHealth.putAll(health);
    }

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void checkAndBroadcast() {
        var health = checkHealth();
        if (!health.equals(previousHealth)) {
            previousHealth.clear();
            previousHealth.putAll(health);
            messagingTemplate.convertAndSend(Topic.LIBRARY_HEALTH.getPath(), new LibraryHealthPayload(health));
        }
    }

    public Map<Long, Boolean> getCurrentHealth() {
        return Map.copyOf(previousHealth);
    }

    private Map<Long, Boolean> checkHealth() {
        return libraryPathRepository.findAllWithLibrary().stream()
                .collect(Collectors.groupingBy(
                        lp -> lp.getLibrary().getId(),
                        Collectors.reducing(true, this::isPathAccessible, Boolean::logicalAnd)
                ));
    }

    private boolean isPathAccessible(LibraryPathEntity libraryPath) {
        var path = Path.of(libraryPath.getPath());
        return Files.exists(path) && Files.isReadable(path);
    }
}
