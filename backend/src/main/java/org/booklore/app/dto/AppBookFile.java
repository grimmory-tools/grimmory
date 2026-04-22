package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppBookFile {
    private Long id;
    private Long bookId;
    private String fileName;
    private boolean isBook;
    private boolean folderBased;
    private String bookType;
    private String archiveType;
    private Long fileSizeKb;
    private String extension;
    private Instant addedOn;
    private boolean isPrimary;
}
