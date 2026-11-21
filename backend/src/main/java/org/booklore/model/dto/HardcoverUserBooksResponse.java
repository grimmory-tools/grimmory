package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HardcoverUserBooksResponse {

    private Data data;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private List<Me> me;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Me {
        @JsonProperty("user_books_aggregate")
        private UserBooksAggregate userBooksAggregate;

        @JsonProperty("user_books")
        private List<UserBook> userBooks;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserBooksAggregate {
        private Aggregate aggregate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Aggregate {
        private Integer count;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserBook {
        private Book book;

        @JsonProperty("edition_id")
        private Integer editionId;

        @JsonProperty("book_id")
        private Integer bookId;

        private Double rating;

        @JsonProperty("last_read_date")
        private String lastReadDate;

        @JsonProperty("status_id")
        private Integer statusId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Book {
        private List<Edition> editions;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Edition {
        @JsonProperty("isbn_13")
        private String isbn13;

        @JsonProperty("isbn_10")
        private String isbn10;
    }
}