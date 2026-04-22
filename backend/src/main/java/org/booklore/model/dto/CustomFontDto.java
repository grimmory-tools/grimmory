package org.booklore.model.dto;

import org.booklore.model.enums.FontFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomFontDto {
    private Long id;
    private String fontName;
    private String originalFileName;
    private FontFormat format;
    private Long fileSize;
    private LocalDateTime uploadedAt;
}
