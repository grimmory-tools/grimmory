package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.DocumentParseStatus;
import org.booklore.util.ArchiveUtils;

import java.time.Instant;

@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookFile {
    private Long id;
    private Long bookId;
    private String fileName;
    private String filePath;
    private String fileSubPath;
    private String sourceArchive;
    private boolean isBook;
    private boolean folderBased;
    private BookFileType bookType;
    private DocumentParseStatus documentParseStatus;
    private ArchiveUtils.ArchiveType archiveType;
    private Long fileSizeKb;
    private String extension;
    private String description;
    private Instant addedOn;
}
