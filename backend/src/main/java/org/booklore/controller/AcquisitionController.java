package org.booklore.controller;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.acquisition.AcquisitionResult;
import org.booklore.model.dto.acquisition.AddToWantedRequest;
import org.booklore.model.dto.acquisition.ClientDTO;
import org.booklore.model.dto.acquisition.ConnectionTestResult;
import org.booklore.model.dto.acquisition.IndexerDTO;
import org.booklore.model.dto.acquisition.JobHistoryDTO;
import org.booklore.model.dto.acquisition.WantedBookDTO;
import org.booklore.service.acquisition.AcquisitionConfigService;
import org.booklore.service.acquisition.AcquisitionService;
import org.booklore.service.acquisition.BookDiscoveryService;
import org.booklore.crons.BookAcquisitionScheduler;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/acquisition")
@AllArgsConstructor
public class AcquisitionController {

    private final AcquisitionConfigService acquisitionConfigService;
    private final BookDiscoveryService bookDiscoveryService;
    private final AcquisitionService acquisitionService;
    private final BookAcquisitionScheduler bookAcquisitionScheduler;

    // ─── Indexers ────────────────────────────────────────────────────────────

    @GetMapping("/indexers")
    public ResponseEntity<List<IndexerDTO>> getIndexers() {
        return ResponseEntity.ok(acquisitionConfigService.getIndexers());
    }

    @PostMapping("/indexers")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<IndexerDTO> createIndexer(@RequestBody IndexerDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(acquisitionConfigService.createIndexer(dto));
    }

    @PutMapping("/indexers/{id}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<IndexerDTO> updateIndexer(@PathVariable Long id, @RequestBody IndexerDTO dto) {
        return ResponseEntity.ok(acquisitionConfigService.updateIndexer(id, dto));
    }

    @DeleteMapping("/indexers/{id}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteIndexer(@PathVariable Long id) {
        acquisitionConfigService.deleteIndexer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/indexers/{id}/test")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<ConnectionTestResult> testIndexer(@PathVariable Long id) {
        return ResponseEntity.ok(acquisitionConfigService.testIndexerConnection(id));
    }

    // ─── Clients ─────────────────────────────────────────────────────────────

    @GetMapping("/clients")
    public ResponseEntity<List<ClientDTO>> getClients() {
        return ResponseEntity.ok(acquisitionConfigService.getClients());
    }

    @PostMapping("/clients")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<ClientDTO> createClient(@RequestBody ClientDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(acquisitionConfigService.createClient(dto));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<ClientDTO> updateClient(@PathVariable Long id, @RequestBody ClientDTO dto) {
        return ResponseEntity.ok(acquisitionConfigService.updateClient(id, dto));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        acquisitionConfigService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clients/{id}/test")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<ConnectionTestResult> testClient(@PathVariable Long id) {
        return ResponseEntity.ok(acquisitionConfigService.testClientConnection(id));
    }

    // ─── Book Discovery ───────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<List<BookMetadata>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String isbn,
            @RequestParam(defaultValue = "0") int page) {

        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(bookDiscoveryService.searchBooks(q, page));
        } else if (isbn != null && !isbn.isBlank()) {
            return ResponseEntity.ok(bookDiscoveryService.searchByIsbn(isbn));
        } else {
            throw ApiError.INVALID_QUERY_PARAMETERS.createException();
        }
    }

    @GetMapping("/library-isbns")
    public ResponseEntity<Set<String>> getLibraryIsbn13s() {
        return ResponseEntity.ok(bookDiscoveryService.getLibraryIsbn13s());
    }

    // ─── Wanted Books ─────────────────────────────────────────────────────────

    @PostMapping("/wanted")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<WantedBookDTO> addToWanted(@RequestBody AddToWantedRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(acquisitionService.addToWanted(request));
    }

    @GetMapping("/wanted")
    public ResponseEntity<List<WantedBookDTO>> getWantedBooks() {
        return ResponseEntity.ok(acquisitionService.getWantedBooks());
    }

    @DeleteMapping("/wanted/{id}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> removeWanted(@PathVariable Long id) {
        acquisitionService.removeWanted(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/wanted/{id}/search")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> triggerSearch(@PathVariable Long id) {
        acquisitionService.triggerSearch(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/wanted/{id}/history")
    public ResponseEntity<List<JobHistoryDTO>> getJobHistory(@PathVariable Long id) {
        return ResponseEntity.ok(acquisitionService.getJobHistory(id));
    }

    // ─── Jobs ─────────────────────────────────────────────────────────────────

    @PostMapping("/jobs/run-now")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> runNow() {
        bookAcquisitionScheduler.triggerNow();
        return ResponseEntity.accepted().build();
    }
}
