package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReadiumSyncRequest {

    @NotNull
    private Long bookId;

    @NotNull
    private Long bookFileId;

    @NotNull
    private Float progressPercent;

    private String readiumLocatorJson;
    private String textBefore;
    private String textHighlight;
    private String textAfter;

    public ReadProgressRequest toReadProgressRequest() {
        ReadProgressRequest req = new ReadProgressRequest();
        req.setBookId(bookId);
        req.setFileProgress(new BookFileProgress(
                bookFileId,
                null,
                null,
                progressPercent,
                null,
                null,
                "READIUM_LOCATOR",
                readiumLocatorJson,
                textBefore,
                textHighlight,
                textAfter
        ));
        return req;
    }
}
