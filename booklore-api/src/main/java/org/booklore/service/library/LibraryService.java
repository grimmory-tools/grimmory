package org.booklore.service.library;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.LibraryMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.LibraryPath;
import org.booklore.model.dto.request.CreateLibraryRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryPathRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.monitoring.MonitoringService;
import org.booklore.task.options.RescanLibraryContext;
import org.booklore.util.FileService;
import org.booklore.util.SecurityContextVirtualThread;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.event.EventListener;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.booklore.service.audit.AuditService;

@Slf4j
@Service
@AllArgsConstructor
@DependsOnDatabaseInitialization
public class LibraryService {

    private static final Set<Long> scanningLibraries = ConcurrentHashMap.newKeySet();

    /**
     * Checks whether a library is currently being scanned.
     * Can be used by other components (e.g., file watcher) to avoid processing
     * files while a full scan is in progress.
     */
    public static boolean isLibraryScanning(long libraryId) {
        return scanningLibraries.contains(libraryId);
    }

    private final LibraryRepository libraryRepository;
    private final LibraryPathRepository libraryPathRepository;
    private final BookRepository bookRepository;
    private final LibraryProcessingService libraryProcessingService;
    private final BookMapper bookMapper;
    private final LibraryMapper libraryMapper;
    private final NotificationService notificationService;
    private final FileService fileService;
    private final MonitoringService monitoringService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void initializeMonitoring() {
        List<Library> libraries = libraryRepository.findAll().stream().map(libraryMapper::toLibrary).collect(Collectors.toList());
        monitoringService.registerLibraries(libraries);
        log.info("Monitoring initialized with {} libraries", libraries.size());
    }

    public Library updateLibrary(CreateLibraryRequest request, Long libraryId) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        library.setName(request.getName());
        library.setIcon(request.getIcon());
        library.setIconType(request.getIconType());
        library.setWatch(request.isWatch());
        library.setFormatPriority(request.getFormatPriority());
        library.setAllowedFormats(request.getAllowedFormats());
        if (request.getMetadataSource() != null) {
            library.setMetadataSource(request.getMetadataSource());
        }

        Set<String> currentPaths = library.getLibraryPaths().stream()
                .map(LibraryPathEntity::getPath)
                .collect(Collectors.toSet());
        Set<String> updatedPaths = request.getPaths().stream()
                .map(LibraryPath::getPath)
                .collect(Collectors.toSet());

        Set<String> deletedPaths = currentPaths.stream()
                .filter(path -> !updatedPaths.contains(path))
                .collect(Collectors.toSet());
        Set<String> newPaths = updatedPaths.stream()
                .filter(path -> !currentPaths.contains(path))
                .collect(Collectors.toSet());

        if (!deletedPaths.isEmpty()) {
            Set<LibraryPathEntity> pathsToRemove = library.getLibraryPaths().stream()
                    .filter(pathEntity -> deletedPaths.contains(pathEntity.getPath()))
                    .collect(Collectors.toSet());

            library.getLibraryPaths().removeAll(pathsToRemove);
            List<Long> books = bookRepository.findAllBookIdsByLibraryPathIdIn(
                    pathsToRemove.stream().map(LibraryPathEntity::getId).collect(Collectors.toSet()));

            if (!books.isEmpty()) {
                notificationService.sendMessage(Topic.BOOKS_REMOVE, books);
            }

            libraryPathRepository.deleteAll(pathsToRemove);
        }

        if (!newPaths.isEmpty()) {
            Set<LibraryPathEntity> newPathEntities = newPaths.stream()
                    .map(path -> LibraryPathEntity.builder().path(path).library(library).build())
                    .collect(Collectors.toSet());

            library.getLibraryPaths().addAll(newPathEntities);
            libraryPathRepository.saveAll(library.getLibraryPaths());
        }

        LibraryEntity savedLibrary = libraryRepository.save(library);

        if (request.isWatch()) {
            monitoringService.registerLibraries(List.of(libraryMapper.toLibrary(savedLibrary)));
        } else {
            monitoringService.unregisterLibrary(libraryId);
        }

        if (!newPaths.isEmpty()) {
            SecurityContextVirtualThread.runWithSecurityContext(() -> {
                if (!scanningLibraries.add(libraryId)) {
                    log.warn("Library {} is already being scanned, skipping duplicate process request", libraryId);
                    return;
                }
                try {
                    libraryProcessingService.processLibrary(libraryId);
                } catch (InvalidDataAccessApiUsageException e) {
                    log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
                } finally {
                    scanningLibraries.remove(libraryId);
                }
                log.info("Parsing task completed!");
            });
        }

        auditService.log(AuditAction.LIBRARY_UPDATED, "Library", libraryId, "Updated library: " + library.getName());
        return libraryMapper.toLibrary(savedLibrary);
    }

    public Library createLibrary(CreateLibraryRequest request) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        Optional<BookLoreUserEntity> user = userRepository.findById(bookLoreUser.getId());

        LibraryEntity libraryEntity = LibraryEntity.builder()
                .name(request.getName())
                .libraryPaths(
                        request.getPaths() == null || request.getPaths().isEmpty() ?
                                Collections.emptyList() :
                                request.getPaths().stream()
                                        .map(path -> LibraryPathEntity.builder().path(path.getPath()).build())
                                        .collect(Collectors.toList())
                )
                .icon(request.getIcon())
                .iconType(request.getIconType())
                .watch(request.isWatch())
                .formatPriority(request.getFormatPriority())
                .allowedFormats(request.getAllowedFormats())
                .metadataSource(request.getMetadataSource())
                .users(List.of(user.get()))
                .build();

        libraryEntity = libraryRepository.save(libraryEntity);
        Long libraryId = libraryEntity.getId();

        if (request.isWatch()) {
            for (LibraryPathEntity pathEntity : libraryEntity.getLibraryPaths()) {
                Path path = Paths.get(pathEntity.getPath());
                monitoringService.registerPath(path, libraryId);
            }
        }

        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            if (!scanningLibraries.add(libraryId)) {
                log.warn("Library {} is already being scanned, skipping duplicate process request", libraryId);
                return;
            }
            try {
                libraryProcessingService.processLibrary(libraryId);
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
            } finally {
                scanningLibraries.remove(libraryId);
            }
            log.info("Parsing task completed!");
        });

        auditService.log(AuditAction.LIBRARY_CREATED, "Library", libraryEntity.getId(), "Created library: " + libraryEntity.getName());
        return libraryMapper.toLibrary(libraryEntity);
    }

    public void rescanLibrary(long libraryId) {
        LibraryEntity lib = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        auditService.log(AuditAction.LIBRARY_SCANNED, "Library", libraryId, "Scanned library: " + lib.getName());

        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            if (!scanningLibraries.add(libraryId)) {
                log.warn("Library {} is already being scanned, skipping duplicate rescan request", libraryId);
                return;
            }
            try {
                RescanLibraryContext context = RescanLibraryContext.builder()
                        .libraryId(libraryId)
                        .build();
                libraryProcessingService.rescanLibrary(context);
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
            } catch (IOException e) {
                log.error("Error while parsing library books", e);
            } finally {
                scanningLibraries.remove(libraryId);
            }
            log.info("Parsing task completed!");
        });
    }

    public Library getLibrary(long libraryId) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        return libraryMapper.toLibrary(libraryEntity);
    }

    public List<Library> getAllLibraries() {
        List<LibraryEntity> libraries = libraryRepository.findAll();
        return libraries.stream().map(libraryMapper::toLibrary).toList();
    }

    public List<Library> getLibraries() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookLoreUserEntity userEntity = userRepository.findById(user.getId()).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        List<LibraryEntity> libraries;
        if (userEntity.getPermissions().isPermissionAdmin()) {
            libraries = libraryRepository.findAll();
        } else {
            List<Long> libraryIds = userEntity.getLibraries().stream().map(LibraryEntity::getId).toList();
            libraries = libraryRepository.findByIdIn(libraryIds);
        }
        return libraries.stream().map(libraryMapper::toLibrary).toList();
    }

    @Transactional
    public void deleteLibrary(long id) {
        LibraryEntity library = libraryRepository.findById(id)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(id));
        monitoringService.unregisterLibrary(id);
        Set<Long> bookIds = library.getBookEntities().stream().map(BookEntity::getId).collect(Collectors.toSet());
        fileService.deleteBookCovers(bookIds);
        String libraryName = library.getName();
        libraryRepository.deleteById(id);
        auditService.log(AuditAction.LIBRARY_DELETED, "Library", id, "Deleted library: " + libraryName);
        log.info("Library deleted successfully: {}", id);
    }

    public Book getBook(long libraryId, long bookId) {
        libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        BookEntity bookEntity = bookRepository.findBookByIdAndLibraryId(bookId, libraryId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        return bookMapper.toBook(bookEntity);
    }

    public List<Book> getBooks(long libraryId) {
        libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        List<BookEntity> bookEntities = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        return bookEntities.stream().map(bookMapper::toBook).toList();
    }

    public Library setFileNamingPattern(long libraryId, String pattern) {
        LibraryEntity library = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        library.setFileNamingPattern(pattern);
        Library result = libraryMapper.toLibrary(libraryRepository.save(library));
        auditService.log(AuditAction.NAMING_PATTERN_CHANGED, "Library", libraryId, "Changed naming pattern for library: " + library.getName() + " to: " + pattern);
        return result;
    }

    public Map<String, Long> getBookCountsByFormat(long libraryId) {
        libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        Map<String, Long> counts = new HashMap<>();
        for (BookFileType type : BookFileType.values()) {
            long count = bookRepository.countByLibraryIdAndBookType(libraryId, type);
            if (count > 0) {
                counts.put(type.name(), count);
            }
        }
        return counts;
    }

    public int scanLibraryPaths(CreateLibraryRequest request) {
        int count = 0;
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            return count;
        }
        Set<BookFileType> allowedFormats = request.getAllowedFormats() != null && !request.getAllowedFormats().isEmpty()
                ? Set.copyOf(request.getAllowedFormats())
                : null;
        for (LibraryPath libraryPath : request.getPaths()) {
            Path path = Paths.get(libraryPath.getPath());
            if (!Files.exists(path)) {
                log.warn("Path does not exist: {}", path);
                continue;
            }
            if (Files.isDirectory(path)) {
                count += scanDirectory(path, allowedFormats);
            } else if (Files.isRegularFile(path) && isProcessableFile(path, allowedFormats)) {
                count++;
            }
        }
        return count;
    }

    private int scanDirectory(Path directory, Set<BookFileType> allowedFormats) {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    count += scanDirectory(entry, allowedFormats);
                } else if (Files.isRegularFile(entry) && isProcessableFile(entry, allowedFormats)) {
                    count++;
                }
            }
        } catch (IOException e) {
            log.error("Error scanning directory: {}", directory, e);
        }
        return count;
    }

    private boolean isProcessableFile(Path file, Set<BookFileType> allowedFormats) {
        String fileName = file.getFileName().toString().toLowerCase();
        for (BookFileType fileType : BookFileType.values()) {
            if (allowedFormats != null && !allowedFormats.contains(fileType)) {
                continue;
            }
            for (String ext : fileType.getExtensions()) {
                if (fileName.endsWith("." + ext)) {
                    return true;
                }
            }
        }
        return false;
    }
}

