package com.adityachandel.booklore.model.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class FileMoveRequest {
    private Set<Long> bookIds;
    private List<Move> moves;

    @Data
    public static class Move {
        private Long bookId;
        private Long targetLibraryId;
        private Long targetLibraryPathId;
    }
}
