package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.config.security.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.dto.request.CreateLibraryRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryPathRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.SecurityContextVirtualThread;
import com.adityachandel.booklore.service.fileprocessor.FileProcessingUtils;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class LibraryService {

    private final LibraryRepository libraryRepository;
    private final LibraryPathRepository libraryPathRepository;
    private final BookRepository bookRepository;
    private final LibraryProcessingService libraryProcessingService;
    private final BookMapper bookMapper;
    private final LibraryMapper libraryMapper;
    private final NotificationService notificationService;
    private final FileProcessingUtils fileProcessingUtils;
    private final MonitoringService monitoringService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    @Transactional
    @PostConstruct
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
        library.setWatch(request.isWatch());

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
                try {
                    libraryProcessingService.processLibrary(libraryId);
                } catch (InvalidDataAccessApiUsageException e) {
                    log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
                } catch (IOException e) {
                    log.error("Error while parsing library books", e);
                }
                log.info("Parsing task completed!");
            });
        }

        return libraryMapper.toLibrary(savedLibrary);
    }

    public Library createLibrary(CreateLibraryRequest request) {
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
                .watch(request.isWatch())
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
            try {
                libraryProcessingService.processLibrary(libraryId);
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
            } catch (IOException e) {
                log.error("Error while parsing library books", e);
            }
            log.info("Parsing task completed!");
        });

        return libraryMapper.toLibrary(libraryEntity);
    }

    public void rescanLibrary(long libraryId) {
        libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                libraryProcessingService.rescanLibrary(libraryId);
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
            } catch (IOException e) {
                log.error("Error while parsing library books", e);
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

    public void deleteLibrary(long id) {
        LibraryEntity library = libraryRepository.findById(id).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(id));
        library.getLibraryPaths().forEach(libraryPath -> {
            Path path = Paths.get(libraryPath.getPath());
            monitoringService.unregisterLibrary(id);
        });
        Set<Long> bookIds = library.getBookEntities().stream().map(BookEntity::getId).collect(Collectors.toSet());
        fileProcessingUtils.deleteBookCovers(bookIds);
        libraryRepository.deleteById(id);
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
        return libraryMapper.toLibrary(libraryRepository.save(library));
    }
}