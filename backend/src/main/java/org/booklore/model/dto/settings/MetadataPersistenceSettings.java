package org.booklore.model.dto.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataPersistenceSettings {
    private SaveToOriginalFile saveToOriginalFile;
    private boolean convertCbrCb7ToCbz;
    private boolean moveFilesToLibraryPattern;
    private SidecarSettings sidecarSettings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveToOriginalFile {
        private FormatSettings epub;
        private FormatSettings pdf;
        private FormatSettings cbx;
        private FormatSettings audiobook;

        public boolean isAnyFormatEnabled() {
            return (epub != null && epub.isEnabled())
                    || (pdf != null && pdf.isEnabled())
                    || (cbx != null && cbx.isEnabled())
                    || (audiobook != null && audiobook.isEnabled());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormatSettings {
        private boolean enabled;
        private int maxFileSizeInMb;
    }
}
