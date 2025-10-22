package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.service.file.FileMoveService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/files")
public class FileMoveController {

    private final FileMoveService fileMoveService;

    @PostMapping("/move")
    public ResponseEntity<?> moveFiles(@RequestBody FileMoveRequest request) {
        fileMoveService.bulkMoveFiles(request);
        return ResponseEntity.ok().build();
    }
}
