package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.reader.CbxReaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cbx")
@RequiredArgsConstructor
public class CbxReaderController {

    private final CbxReaderService cbxReaderService;

    @GetMapping("/{bookId}/pages")
    public List<Integer> listPages(@PathVariable Long bookId) {
        return cbxReaderService.getAvailablePages(bookId);
    }
}