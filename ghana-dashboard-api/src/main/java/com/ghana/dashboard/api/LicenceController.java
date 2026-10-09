package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.LicenceDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.LicenceService;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class LicenceController {

    private final LicenceService licences;

    public LicenceController(LicenceService licences) {
        this.licences = licences;
    }

    /** Optional document so a licence can be registered with its scan in one step. */
    @PostMapping(value = "/licences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LicenceDtos.LicenceResponse>> create(
            @Valid @RequestPart("licence") LicenceDtos.LicenceCreateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Licence added",
                licences.create(CurrentUser.getShopId(), CurrentUser.getUserId(), request, file)));
    }

    @GetMapping("/licences")
    public ResponseEntity<ApiResponse<List<LicenceDtos.LicenceResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.ok(licences.list(CurrentUser.getShopId(), status, type)));
    }

    @GetMapping("/licences/summary")
    public ResponseEntity<ApiResponse<LicenceDtos.LicenceSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(licences.summary(CurrentUser.getShopId())));
    }

    @GetMapping("/licences/{id}")
    public ResponseEntity<ApiResponse<LicenceDtos.LicenceResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(licences.get(CurrentUser.getShopId(), id)));
    }

    @PutMapping("/licences/{id}")
    public ResponseEntity<ApiResponse<LicenceDtos.LicenceResponse>> update(
            @PathVariable Long id, @Valid @RequestBody LicenceDtos.LicenceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Licence updated",
                licences.update(CurrentUser.getShopId(), id, request)));
    }

    @DeleteMapping("/licences/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        licences.delete(CurrentUser.getShopId(), id);
        return ResponseEntity.ok(ApiResponse.success(200, "Licence deleted"));
    }

    /** Replaces the stored scan; the previous file is dropped once the row points at the new one. */
    @PostMapping(value = "/licences/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LicenceDtos.LicenceResponse>> uploadDocument(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Document uploaded",
                licences.attachDocument(CurrentUser.getShopId(), id, file)));
    }

    @GetMapping("/licences/{id}/document")
    public ResponseEntity<Resource> document(@PathVariable Long id,
                                             @RequestParam(defaultValue = "false") boolean download) {
        return serve(licences.document(CurrentUser.getShopId(), id), download);
    }

    // ---- renewals ----

    /** {@code file} is optional: renewing without one is the "Mark renewed" path. */
    @PostMapping(value = "/licences/{id}/renew", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LicenceDtos.LicenceResponse>> renew(
            @PathVariable Long id,
            @Valid @RequestPart("renewal") LicenceDtos.RenewalRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Licence renewed",
                licences.renew(CurrentUser.getShopId(), CurrentUser.getUserId(), id, request, file)));
    }

    @GetMapping("/licences/{id}/renewals")
    public ResponseEntity<ApiResponse<List<LicenceDtos.RenewalResponse>>> renewals(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(licences.renewals(CurrentUser.getShopId(), id)));
    }

    @GetMapping("/licences/{id}/renewals/{renewalId}/document")
    public ResponseEntity<Resource> renewalDocument(@PathVariable Long id, @PathVariable Long renewalId,
                                                    @RequestParam(defaultValue = "false") boolean download) {
        return serve(licences.renewalDocument(CurrentUser.getShopId(), id, renewalId), download);
    }

    private static ResponseEntity<Resource> serve(LicenceService.Document file, boolean download) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(file.resource());
    }
}