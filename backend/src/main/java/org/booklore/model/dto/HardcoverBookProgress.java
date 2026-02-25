package org.booklore.model.dto;

import java.util.Date;
import java.util.List;
import org.booklore.model.enums.ReadStatus;

/**
 * Helper class to hold Hardcover book information.
 */
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

    public Long getBookloreBookId() {
        return bookloreBookId;
    }

    public void setBookloreBookId(Long bookloreBookId) {
        this.bookloreBookId = bookloreBookId;
    }

    public String getHardcoverId() {
        return hardcoverId;
    }

    public void setHardcoverId(String hardcoverId) {
        this.hardcoverId = hardcoverId;
    }

    public Integer getEditionId() {
        return editionId;
    }

    public void setEditionId(Integer editionId) {
        this.editionId = editionId;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getIsbn10() {
        return isbn10;
    }

    public void setIsbn10(List<String> isbn10) {
        this.isbn10 = isbn10;
    }

    public List<String> getIsbn13() {
        return isbn13;
    }

    public void setIsbn13(List<String> isbn13) {
        this.isbn13 = isbn13;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Date getLastReadDate() {
        return lastReadDate;
    }

    public void setLastReadDate(Date lastReadDate) {
        this.lastReadDate = lastReadDate;
    }

    public ReadStatus getStatus() {
        return status;
    }

    public void setStatus(ReadStatus status) {
        this.status = status;
    }
}
