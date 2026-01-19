package com.adityachandel.booklore.service.komga;

import com.adityachandel.booklore.mapper.komga.KomgaMapper;
import com.adityachandel.booklore.model.dto.MagicShelf;
import com.adityachandel.booklore.model.dto.komga.*;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.MagicShelfService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.reader.CbxReaderService;
import com.adityachandel.booklore.service.reader.PdfReaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KomgaService {

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final KomgaMapper komgaMapper;
    private final MagicShelfService magicShelfService;
    private final CbxReaderService cbxReaderService;
    private final PdfReaderService pdfReaderService;
    private final AppSettingService appSettingService;

    public List<KomgaLibraryDto> getAllLibraries() {
        return libraryRepository.findAll().stream()
                .map(komgaMapper::toKomgaLibraryDto)
                .collect(Collectors.toList());
    }

    public KomgaLibraryDto getLibraryById(Long libraryId) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new RuntimeException("Library not found"));
        return komgaMapper.toKomgaLibraryDto(library);
    }

    public KomgaPageableDto<KomgaSeriesDto> getAllSeries(Long libraryId, int page, int size, boolean unpaged) {
        log.debug("Getting all series for libraryId: {}, page: {}, size: {}", libraryId, page, size);
        
        // Check if we should group unknown series
        boolean groupUnknown = appSettingService.getAppSettings().isKomgaGroupUnknown();
        
        // Get distinct series names directly from database (MUCH faster than loading all books)
        List<String> sortedSeriesNames;
        if (groupUnknown) {
            // Use optimized query that groups books without series as "Unknown Series"
            if (libraryId != null) {
                sortedSeriesNames = bookRepository.findDistinctSeriesNamesGroupedByLibraryId(
                    libraryId, komgaMapper.getUnknownSeriesName());
            } else {
                sortedSeriesNames = bookRepository.findDistinctSeriesNamesGrouped(
                    komgaMapper.getUnknownSeriesName());
            }
        } else {
            // Use query that gives each book without series its own entry
            if (libraryId != null) {
                sortedSeriesNames = bookRepository.findDistinctSeriesNamesUngroupedByLibraryId(libraryId);
            } else {
                sortedSeriesNames = bookRepository.findDistinctSeriesNamesUngrouped();
            }
        }
        
        log.debug("Found {} distinct series names from database (optimized)", sortedSeriesNames.size());
        
        // Calculate pagination
        int totalElements = sortedSeriesNames.size();
        List<String> pageSeriesNames;
        int actualPage;
        int actualSize;
        int totalPages;
        
        if (unpaged) {
            pageSeriesNames = sortedSeriesNames;
            actualPage = 0;
            actualSize = totalElements;
            totalPages = totalElements > 0 ? 1 : 0;
        } else {
            totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = Math.min(page * size, totalElements);
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            pageSeriesNames = sortedSeriesNames.subList(fromIndex, toIndex);
            actualPage = page;
            actualSize = size;
        }
        
        // Now load books only for the series on this page (optimized - only loads what's needed)
        List<KomgaSeriesDto> content = new ArrayList<>();
        for (String seriesName : pageSeriesNames) {
            try {
                // Load only the books for this specific series
                List<BookEntity> seriesBooks;
                if (libraryId != null) {
                    if (groupUnknown) {
                        seriesBooks = bookRepository.findBooksBySeriesNameGroupedByLibraryId(
                            seriesName, libraryId, komgaMapper.getUnknownSeriesName());
                    } else {
                        seriesBooks = bookRepository.findBooksBySeriesNameUngroupedByLibraryId(
                            seriesName, libraryId);
                    }
                } else {
                    // For all libraries, need to load all books and filter (less common case)
                    List<BookEntity> allBooks = bookRepository.findAllWithMetadata();
                    seriesBooks = allBooks.stream()
                            .filter(book -> komgaMapper.getBookSeriesName(book).equals(seriesName))
                            .collect(Collectors.toList());
                }
                
                if (!seriesBooks.isEmpty()) {
                    Long libId = seriesBooks.get(0).getLibrary().getId();
                    KomgaSeriesDto seriesDto = komgaMapper.toKomgaSeriesDto(seriesName, libId, seriesBooks);
                    if (seriesDto != null) {
                        content.add(seriesDto);
                    }
                }
            } catch (Exception e) {
                log.error("Error mapping series: {}", seriesName, e);
            }
        }
        
        log.debug("Mapped {} series DTOs for this page", content.size());
        
        return KomgaPageableDto.<KomgaSeriesDto>builder()
                .content(content)
                .number(actualPage)
                .size(actualSize)
                .numberOfElements(content.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(actualPage == 0)
                .last(totalElements == 0 || actualPage >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }

    public KomgaSeriesDto getSeriesById(String seriesId) {
        // Parse seriesId to extract library and series name
        String[] parts = seriesId.split("-", 2);
        if (parts.length < 2) {
            throw new RuntimeException("Invalid series ID");
        }
        
        Long libraryId = Long.parseLong(parts[0]);
        String seriesSlug = parts[1];
        
        // Get books matching the series - optimized to query by series name
        List<BookEntity> allSeriesBooks = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        
        // Find the series name that matches this slug
        List<BookEntity> seriesBooks = allSeriesBooks.stream()
                .filter(book -> {
                    String bookSeriesName = komgaMapper.getBookSeriesName(book);
                    String bookSeriesSlug = bookSeriesName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
                    return bookSeriesSlug.equals(seriesSlug);
                })
                .collect(Collectors.toList());
        
        if (seriesBooks.isEmpty()) {
            throw new RuntimeException("Series not found");
        }
        
        String seriesName = komgaMapper.getBookSeriesName(seriesBooks.get(0));
        
        return komgaMapper.toKomgaSeriesDto(seriesName, libraryId, seriesBooks);
    }

    public KomgaPageableDto<KomgaBookDto> getBooksBySeries(String seriesId, int page, int size, boolean unpaged) {
        // Parse seriesId to extract library and series name
        String[] parts = seriesId.split("-", 2);
        if (parts.length < 2) {
            throw new RuntimeException("Invalid series ID");
        }
        
        Long libraryId = Long.parseLong(parts[0]);
        String seriesSlug = parts[1];
        
        // Get all books for the library once
        List<BookEntity> allBooks = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        
        // Filter and sort books for this series
        List<BookEntity> seriesBooks = allBooks.stream()
                .filter(book -> {
                    String bookSeriesName = komgaMapper.getBookSeriesName(book);
                    String bookSeriesSlug = bookSeriesName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
                    return bookSeriesSlug.equals(seriesSlug);
                })
                .sorted(Comparator.comparing(book -> {
                    BookMetadataEntity metadata = book.getMetadata();
                    return metadata != null && metadata.getSeriesNumber() != null 
                         ? metadata.getSeriesNumber() 
                         : 0f;
                }))
                .collect(Collectors.toList());
        
        // Handle unpaged mode
        int totalElements = seriesBooks.size();
        List<KomgaBookDto> content;
        int actualPage;
        int actualSize;
        int totalPages;
        
        if (unpaged) {
            // Return all books without pagination
            content = seriesBooks.stream()
                    .map(book -> komgaMapper.toKomgaBookDto(book))
                    .collect(Collectors.toList());
            actualPage = 0;
            actualSize = totalElements;
            totalPages = totalElements > 0 ? 1 : 0;
        } else {
            // Paginate
            totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = Math.min(page * size, totalElements);
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            content = seriesBooks.subList(fromIndex, toIndex).stream()
                    .map(book -> komgaMapper.toKomgaBookDto(book))
                    .collect(Collectors.toList());
            actualPage = page;
            actualSize = size;
        }
        
        return KomgaPageableDto.<KomgaBookDto>builder()
                .content(content)
                .number(actualPage)
                .size(actualSize)
                .numberOfElements(content.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(actualPage == 0)
                .last(totalElements == 0 || actualPage >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }

    public KomgaPageableDto<KomgaBookDto> getAllBooks(Long libraryId, int page, int size) {
        List<BookEntity> books;
        
        if (libraryId != null) {
            books = bookRepository.findAllWithMetadataByLibraryId(libraryId);
        } else {
            books = bookRepository.findAllWithMetadata();
        }
        
        // Manual pagination
        int totalElements = books.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        
        List<KomgaBookDto> content = books.subList(fromIndex, toIndex).stream()
                .map(book -> komgaMapper.toKomgaBookDto(book))
                .collect(Collectors.toList());
        
        return KomgaPageableDto.<KomgaBookDto>builder()
                .content(content)
                .number(page)
                .size(size)
                .numberOfElements(content.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }

    public KomgaBookDto getBookById(Long bookId) {
        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        return komgaMapper.toKomgaBookDto(book);
    }

    public List<KomgaPageDto> getBookPages(Long bookId) {
        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        
        BookMetadataEntity metadata = book.getMetadata();
        Integer pageCount = metadata != null && metadata.getPageCount() != null ? metadata.getPageCount() : 0;
        
        List<KomgaPageDto> pages = new ArrayList<>();
        if (pageCount > 0) {
            for (int i = 1; i <= pageCount; i++) {
                pages.add(KomgaPageDto.builder()
                        .number(i)
                        .fileName("page-" + i)
                        .mediaType("image/jpeg")
                        .build());
            }
        }
        
        return pages;
    }

    private Map<String, List<BookEntity>> groupBooksBySeries(List<BookEntity> books) {
        Map<String, List<BookEntity>> seriesMap = new HashMap<>();
        
        for (BookEntity book : books) {
            String seriesName = komgaMapper.getBookSeriesName(book);
            seriesMap.computeIfAbsent(seriesName, k -> new ArrayList<>()).add(book);
        }
        
        return seriesMap;
    }
    
    public KomgaPageableDto<KomgaCollectionDto> getCollections(int page, int size, boolean unpaged) {
        log.debug("Getting collections, page: {}, size: {}, unpaged: {}", page, size, unpaged);
        
        List<MagicShelf> magicShelves = magicShelfService.getUserShelves();
        log.debug("Found {} magic shelves", magicShelves.size());
        
        // Convert to collection DTOs - for now, series count is 0 since we don't have 
        // the series filter implementation
        List<KomgaCollectionDto> allCollections = magicShelves.stream()
                .map(shelf -> komgaMapper.toKomgaCollectionDto(shelf, 0))
                .sorted(Comparator.comparing(KomgaCollectionDto::getName))
                .collect(Collectors.toList());
        
        log.debug("Mapped to {} collection DTOs", allCollections.size());
        
        // Handle unpaged mode
        int totalElements = allCollections.size();
        List<KomgaCollectionDto> content;
        int actualPage;
        int actualSize;
        int totalPages;
        
        if (unpaged) {
            content = allCollections;
            actualPage = 0;
            actualSize = totalElements;
            totalPages = totalElements > 0 ? 1 : 0;
        } else {
            // Paginate
            totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = Math.min(page * size, totalElements);
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            content = allCollections.subList(fromIndex, toIndex);
            actualPage = page;
            actualSize = size;
        }
        
        return KomgaPageableDto.<KomgaCollectionDto>builder()
                .content(content)
                .number(actualPage)
                .size(actualSize)
                .numberOfElements(content.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(actualPage == 0)
                .last(totalElements == 0 || actualPage >= totalPages - 1)
                .empty(content.isEmpty())
                .build();
    }
    
    public Resource getBookPageImage(Long bookId, Integer pageNumber, boolean convertToPng) throws IOException {
        log.debug("Getting page {} from book {} (convert to PNG: {})", pageNumber, bookId, convertToPng);
        
        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found: " + bookId));

        boolean isPDF = book.getPrimaryBookFile().getBookType() == BookFileType.PDF;
     
        // Stream the page to a ByteArrayOutputStream
        // streamPageImage will throw if page does not exist
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Make sure pages are cached
        if (isPDF) {
            cbxReaderService.getAvailablePages(bookId);
            cbxReaderService.streamPageImage(bookId, pageNumber, outputStream);
        } else {
            pdfReaderService.getAvailablePages(bookId);
            pdfReaderService.streamPageImage(bookId, pageNumber, outputStream);
        }
        
        byte[] imageData = outputStream.toByteArray();
        
        // If conversion to PNG is requested, convert the image
        if (convertToPng) {
            imageData = convertImageToPng(imageData);
        }
        
        return new ByteArrayResource(imageData);
    }
    
    private byte[] convertImageToPng(byte[] imageData) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("Failed to read image data");
            }
            
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}