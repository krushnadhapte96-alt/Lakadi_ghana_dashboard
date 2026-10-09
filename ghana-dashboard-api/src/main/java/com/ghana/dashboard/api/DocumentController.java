package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.DocumentDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentDtos.DocumentResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam("title") String title,
            @RequestParam(value = "batchId", required = false) Long batchId,
            @RequestParam(value = "notes", required = false) String notes) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Document uploaded",
                documents.upload(CurrentUser.getShopId(), CurrentUser.getUserId(), file, type, title, batchId, notes)));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<DocumentDtos.DocumentResponse>>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(documents.list(CurrentUser.getShopId(), type, batchId, q)));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<DocumentDtos.DocumentResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(documents.get(CurrentUser.getShopId(), id)));
    }

    @PutMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<DocumentDtos.DocumentResponse>> update(
            @PathVariable Long id, @Valid @RequestBody DocumentDtos.DocumentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(documents.update(CurrentUser.getShopId(), id, request)));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        documents.delete(CurrentUser.getShopId(), id);
        return ResponseEntity.ok(ApiResponse.success(200, "Document deleted"));
    }

    @GetMapping("/batches/{id}/documents")
    public ResponseEntity<ApiResponse<List<DocumentDtos.DocumentResponse>>> batchDocuments(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(documents.list(CurrentUser.getShopId(), null, id, null)));
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> file(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        DocumentService.Download file = documents.download(CurrentUser.getShopId(), id);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(file.resource());
    }

    // ---- batch quality notes ----

    @GetMapping("/batches/{id}/quality-notes")
    public ResponseEntity<ApiResponse<List<DocumentDtos.QualityNoteResponse>>> qualityNotes(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(documents.qualityNotes(CurrentUser.getShopId(), id)));
    }

    @PostMapping("/batches/{id}/quality-notes")
    public ResponseEntity<ApiResponse<DocumentDtos.QualityNoteResponse>> addQualityNote(
            @PathVariable Long id, @Valid @RequestBody DocumentDtos.QualityNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(documents.addQualityNote(CurrentUser.getShopId(), id, request)));
    }

    @PutMapping("/batches/{id}/quality-notes/{noteId}")
    public ResponseEntity<ApiResponse<DocumentDtos.QualityNoteResponse>> updateQualityNote(
            @PathVariable Long id, @PathVariable Long noteId, @Valid @RequestBody DocumentDtos.QualityNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(documents.updateQualityNote(CurrentUser.getShopId(), id, noteId, request)));
    }

    @DeleteMapping("/batches/{id}/quality-notes/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteQualityNote(@PathVariable Long id, @PathVariable Long noteId) {
        documents.deleteQualityNote(CurrentUser.getShopId(), id, noteId);
        return ResponseEntity.ok(ApiResponse.success(200, "Quality note deleted"));
    }
}
