package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.convertor.SortConverter;
import com.adityachandel.booklore.model.dto.Sort;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "library")
public class LibraryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Convert(converter = SortConverter.class)
    private Sort sort;

    @OneToMany(mappedBy = "library", orphanRemoval = true)
    private List<BookEntity> bookEntities;

    @OneToMany(mappedBy = "library", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<LibraryPathEntity> libraryPaths;

    @ManyToMany(mappedBy = "libraries")
    private List<BookLoreUserEntity> users;

    private boolean watch;

    private String icon;

    @Column(name = "file_naming_pattern")
    private String fileNamingPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_mode", nullable = false)
    private LibraryScanMode scanMode = LibraryScanMode.FILE_AS_BOOK;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_book_format")
    private BookFileType defaultBookFormat;
}
