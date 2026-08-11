package org.booklore.model.dto;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.booklore.model.enums.ReadStatus;

/**
 * Helper class to hold Hardcover book information.
 */
@Getter
@Setter
public class HardcoverBookProgress {
    private Long bookloreBookId = null;
    private String hardcoverId;
    private Integer editionId;
    private Integer pages;
    private String title;
    private List<String> isbn10 = null;
    private List<String> isbn13 = null;
    private Integer rating = null;
    private Date lastReadDate = null;
    private ReadStatus status;
}
