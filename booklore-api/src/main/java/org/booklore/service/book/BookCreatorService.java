package org.booklore.service.book;

import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.repository.*;
import org.booklore.service.file.FileFingerprint;
import org.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BookCreatorService {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final MoodRepository moodRepository;
    private final TagRepository tagRepository;
    private final BookRepository bookRepository;
    private final BookMetadataRepository bookMetadataRepository;
    private final ComicMetadataRepository comicMetadataRepository;
    private final ComicCharacterRepository comicCharacterRepository;
    private final ComicTeamRepository comicTeamRepository;
    private final ComicLocationRepository comicLocationRepository;
    private final ComicCreatorRepository comicCreatorRepository;

    // Temporary storage for comic metadata DTOs during processing
    private final Map<Long, ComicMetadata> pendingComicMetadata = new ConcurrentHashMap<>();

    public BookCreatorService(AuthorRepository authorRepository,
                              CategoryRepository categoryRepository,
                              MoodRepository moodRepository,
                              TagRepository tagRepository,
                              BookRepository bookRepository,
                              BookMetadataRepository bookMetadataRepository,
                              ComicMetadataRepository comicMetadataRepository,
                              ComicCharacterRepository comicCharacterRepository,
                              ComicTeamRepository comicTeamRepository,
                              ComicLocationRepository comicLocationRepository,
                              ComicCreatorRepository comicCreatorRepository) {
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.moodRepository = moodRepository;
        this.tagRepository = tagRepository;
        this.bookRepository = bookRepository;
        this.bookMetadataRepository = bookMetadataRepository;
        this.comicMetadataRepository = comicMetadataRepository;
        this.comicCharacterRepository = comicCharacterRepository;
        this.comicTeamRepository = comicTeamRepository;
        this.comicLocationRepository = comicLocationRepository;
        this.comicCreatorRepository = comicCreatorRepository;
    }

    public BookEntity createShellBook(LibraryFile libraryFile, BookFileType bookFileType) {
        Optional<BookEntity> existingBookOpt = bookRepository.findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
                libraryFile.getLibraryEntity().getId(),
                libraryFile.getLibraryPathEntity().getId(),
                libraryFile.getFileSubPath(),
                libraryFile.getFileName());

        if (existingBookOpt.isPresent()) {
            log.warn("Book already exists for file: {}", libraryFile.getFileName());
            long fileSizeKb = calculateFileSize(libraryFile);
            String newHash = libraryFile.isFolderBased()
                    ? FileFingerprint.generateFolderHash(libraryFile.getFullPath())
                    : FileFingerprint.generateHash(libraryFile.getFullPath());
            BookEntity existingBook = existingBookOpt.get();
            BookFileEntity primaryFile = existingBook.getPrimaryBookFile();
            primaryFile.setCurrentHash(newHash);
            primaryFile.setInitialHash(newHash);
            primaryFile.setFileSizeKb(fileSizeKb);
            primaryFile.setFolderBased(libraryFile.isFolderBased());
            existingBook.setDeleted(false);
            return existingBook;
        }

        long fileSizeKb = calculateFileSize(libraryFile);
        String hash = libraryFile.isFolderBased()
                ? FileFingerprint.generateFolderHash(libraryFile.getFullPath())
                : FileFingerprint.generateHash(libraryFile.getFullPath());

        BookEntity bookEntity = BookEntity.builder()
                .library(libraryFile.getLibraryEntity())
                .libraryPath(libraryFile.getLibraryPathEntity())
                .addedOn(Instant.now())
                .bookFiles(new ArrayList<>())
                .build();

        BookFileEntity bookFileEntity = BookFileEntity.builder()
                .book(bookEntity)
                .fileName(libraryFile.getFileName())
                .fileSubPath(libraryFile.getFileSubPath())
                .isBookFormat(true)
                .folderBased(libraryFile.isFolderBased())
                .bookType(bookFileType)
                .fileSizeKb(fileSizeKb)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();
        bookEntity.getBookFiles().add(bookFileEntity);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity)
                .build();
        bookEntity.setMetadata(metadata);

        return bookRepository.saveAndFlush(bookEntity);
    }

    private long calculateFileSize(LibraryFile libraryFile) {
        if (libraryFile.isFolderBased()) {
            Long size = FileUtils.getFolderSizeInKb(libraryFile.getFullPath());
            return size != null ? size : 0L;
        } else {
            Long size = FileUtils.getFileSizeInKb(libraryFile.getFullPath());
            return size != null ? size : 0L;
        }
    }

    public void addCategoriesToBook(Set<String> categories, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getCategories() == null) {
            bookEntity.getMetadata().setCategories(new HashSet<>());
        }
        categories.stream()
                .map(cat -> truncate(cat, 255))
                .map(truncated -> categoryRepository.findByName(truncated)
                        .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(truncated).build())))
                .forEach(catEntity -> bookEntity.getMetadata().getCategories().add(catEntity));
    }

    public void addAuthorsToBook(Set<String> authors, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() == null) {
            bookEntity.getMetadata().setAuthors(new HashSet<>());
        }
        authors.stream()
                .map(authorName -> truncate(authorName, 255))
                .map(authorName -> authorRepository.findByName(authorName)
                        .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(authorName).build())))
                .forEach(authorEntity -> bookEntity.getMetadata().getAuthors().add(authorEntity));
        bookEntity.getMetadata().updateSearchText(); // Manually trigger search text update since collection modification doesn't trigger @PreUpdate
    }

    public void addMoodsToBook(Set<String> moods, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getMoods() == null) {
            bookEntity.getMetadata().setMoods(new HashSet<>());
        }
        moods.stream()
                .map(mood -> truncate(mood, 255))
                .map(truncated -> moodRepository.findByName(truncated)
                        .orElseGet(() -> moodRepository.save(MoodEntity.builder().name(truncated).build())))
                .forEach(moodEntity -> bookEntity.getMetadata().getMoods().add(moodEntity));
    }

    public void addTagsToBook(Set<String> tags, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getTags() == null) {
            bookEntity.getMetadata().setTags(new HashSet<>());
        }
        tags.stream()
                .map(tag -> truncate(tag, 255))
                .map(truncated -> tagRepository.findByName(truncated)
                        .orElseGet(() -> tagRepository.save(TagEntity.builder().name(truncated).build())))
                .forEach(tagEntity -> bookEntity.getMetadata().getTags().add(tagEntity));
    }

    private String truncate(String input, int maxLength) {
        if (input == null)
            return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }

    public void saveConnections(BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() != null && !bookEntity.getMetadata().getAuthors().isEmpty()) {
            authorRepository.saveAll(bookEntity.getMetadata().getAuthors());
        }
        bookRepository.save(bookEntity);
        bookMetadataRepository.save(bookEntity.getMetadata());

        // Save comic metadata if present
        ComicMetadataEntity comicMetadata = bookEntity.getMetadata().getComicMetadata();
        if (comicMetadata != null && comicMetadata.getBookId() != null) {
            comicMetadataRepository.save(comicMetadata);

            // Populate relationships from pending DTO
            ComicMetadata comicDto = pendingComicMetadata.remove(bookEntity.getId());
            if (comicDto != null) {
                populateComicMetadataRelationships(comicMetadata, comicDto);
            }
        }
    }

    public void setComicMetadataDto(BookEntity bookEntity, ComicMetadata comicDto) {
        if (bookEntity.getId() != null && comicDto != null) {
            pendingComicMetadata.put(bookEntity.getId(), comicDto);
        }
    }

    private void populateComicMetadataRelationships(ComicMetadataEntity comic, ComicMetadata dto) {
        // Add characters
        if (dto.getCharacters() != null && !dto.getCharacters().isEmpty()) {
            if (comic.getCharacters() == null) {
                comic.setCharacters(new HashSet<>());
            }
            dto.getCharacters().stream()
                    .map(name -> truncate(name, 255))
                    .map(name -> comicCharacterRepository.findByName(name)
                            .orElseGet(() -> comicCharacterRepository.save(ComicCharacterEntity.builder().name(name).build())))
                    .forEach(entity -> comic.getCharacters().add(entity));
        }

        // Add teams
        if (dto.getTeams() != null && !dto.getTeams().isEmpty()) {
            if (comic.getTeams() == null) {
                comic.setTeams(new HashSet<>());
            }
            dto.getTeams().stream()
                    .map(name -> truncate(name, 255))
                    .map(name -> comicTeamRepository.findByName(name)
                            .orElseGet(() -> comicTeamRepository.save(ComicTeamEntity.builder().name(name).build())))
                    .forEach(entity -> comic.getTeams().add(entity));
        }

        // Add locations
        if (dto.getLocations() != null && !dto.getLocations().isEmpty()) {
            if (comic.getLocations() == null) {
                comic.setLocations(new HashSet<>());
            }
            dto.getLocations().stream()
                    .map(name -> truncate(name, 255))
                    .map(name -> comicLocationRepository.findByName(name)
                            .orElseGet(() -> comicLocationRepository.save(ComicLocationEntity.builder().name(name).build())))
                    .forEach(entity -> comic.getLocations().add(entity));
        }

        // Add creators with roles
        if (comic.getCreatorMappings() == null) {
            comic.setCreatorMappings(new HashSet<>());
        }
        addCreatorsWithRole(comic, dto.getPencillers(), ComicCreatorRole.PENCILLER);
        addCreatorsWithRole(comic, dto.getInkers(), ComicCreatorRole.INKER);
        addCreatorsWithRole(comic, dto.getColorists(), ComicCreatorRole.COLORIST);
        addCreatorsWithRole(comic, dto.getLetterers(), ComicCreatorRole.LETTERER);
        addCreatorsWithRole(comic, dto.getCoverArtists(), ComicCreatorRole.COVER_ARTIST);
        addCreatorsWithRole(comic, dto.getEditors(), ComicCreatorRole.EDITOR);

        // Save the updated comic metadata with relationships
        comicMetadataRepository.save(comic);
    }

    private void addCreatorsWithRole(ComicMetadataEntity comic, Set<String> names, ComicCreatorRole role) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            String truncatedName = truncate(name, 255);
            ComicCreatorEntity creator = comicCreatorRepository.findByName(truncatedName)
                    .orElseGet(() -> comicCreatorRepository.save(ComicCreatorEntity.builder().name(truncatedName).build()));

            ComicCreatorMappingEntity mapping = ComicCreatorMappingEntity.builder()
                    .comicMetadata(comic)
                    .creator(creator)
                    .role(role)
                    .build();
            comic.getCreatorMappings().add(mapping);
        }
    }
}
