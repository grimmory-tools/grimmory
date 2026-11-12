package org.booklore.model.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class FileMoveRequest {
    private List<Move> moves;

    @Data
    public static class Move {
        private Long bookId;
        private Long targetLibraryId;
        private Long targetLibraryPathId;
    }
}
