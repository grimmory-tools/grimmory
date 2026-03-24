package org.booklore.model.entity;

import org.booklore.convertor.FormatPriorityConverter;
import org.booklore.convertor.SortConverter;
import org.booklore.model.dto.Sort;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.IconType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.model.enums.MetadataSource;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
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

    @OneToMany(mappedBy = "library", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LibraryPathEntity> libraryPaths;

    @ManyToMany(mappedBy = "libraries")
    private List<BookLoreUserEntity> users;

    private boolean watch;

    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type")
    private IconType iconType;

    @Column(name = "file_naming_pattern")
    private String fileNamingPattern;

    @Convert(converter = FormatPriorityConverter.class)
    @Column(name = "format_priority")
    @Builder.Default
    private List<BookFileType> formatPriority = new ArrayList<>();

    @Convert(converter = FormatPriorityConverter.class)
    @Column(name = "allowed_formats")
    private List<BookFileType> allowedFormats;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_mode")
    @Builder.Default
    private LibraryOrganizationMode organizationMode = LibraryOrganizationMode.AUTO_DETECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_source")
    @Builder.Default
    private MetadataSource metadataSource = MetadataSource.EMBEDDED;

}
