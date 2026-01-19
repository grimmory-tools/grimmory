package com.adityachandel.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaLibraryDto {
    private String id;
    private String name;
    private String root;
    private Boolean unavailable;
    
    // Scan options
    @Builder.Default
    private Boolean scanCbx = true;
    @Builder.Default
    private Boolean scanEpub = true;
    @Builder.Default
    private Boolean scanPdf = true;
    @Builder.Default
    private Boolean scanOnStartup = false;
    @Builder.Default
    private String scanInterval = "EVERY_6H";
    @Builder.Default
    private Boolean scanForceModifiedTime = false;
    
    // Import options
    @Builder.Default
    private Boolean importComicInfoBook = true;
    @Builder.Default
    private Boolean importComicInfoSeries = true;
    @Builder.Default
    private Boolean importComicInfoCollection = true;
    @Builder.Default
    private Boolean importComicInfoReadList = true;
    @Builder.Default
    private Boolean importComicInfoSeriesAppendVolume = false;
    @Builder.Default
    private Boolean importEpubBook = true;
    @Builder.Default
    private Boolean importEpubSeries = true;
    @Builder.Default
    private Boolean importLocalArtwork = true;
    @Builder.Default
    private Boolean importBarcodeIsbn = true;
    @Builder.Default
    private Boolean importMylarSeries = true;
    
    // Other options
    @Builder.Default
    private Boolean repairExtensions = false;
    @Builder.Default
    private Boolean convertToCbz = false;
    @Builder.Default
    private Boolean emptyTrashAfterScan = false;
    @Builder.Default
    private String seriesCover = "FIRST";
    @Builder.Default
    private Boolean hashFiles = true;
    @Builder.Default
    private Boolean hashPages = false;
    @Builder.Default
    private Boolean hashKoreader = false;
    @Builder.Default
    private Boolean analyzeDimensions = true;
}
