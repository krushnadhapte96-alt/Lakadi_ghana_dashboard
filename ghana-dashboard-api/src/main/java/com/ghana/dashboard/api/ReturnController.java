package com.ghana.dashboard.api;

import com.ghana.dashboard.common.ApiResponse;
import com.ghana.dashboard.dto.ReturnDtos;
import com.ghana.dashboard.security.CurrentUser;
import com.ghana.dashboard.service.ReturnService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<ReturnDtos.LookupResponse>> lookup(@RequestParam String billNo) {
        return ResponseEntity.ok(ApiResponse.ok(returnService.lookup(CurrentUser.getShopId(), billNo)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReturnDtos.ReturnDetailResponse>> create(
            @Valid @RequestBody ReturnDtos.CreateReturnRequest request) {
        ReturnDtos.ReturnDetailResponse created =
                returnService.create(CurrentUser.getShopId(), CurrentUser.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReturnDtos.ReturnSummaryResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.ok(returnService.list(CurrentUser.getShopId(),
                new ReturnDtos.ReturnFilter(status, type, dateFrom, dateTo))));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ReturnDtos.ReturnStatsResponse>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.ok(returnService.stats(CurrentUser.getShopId(), dateFrom, dateTo)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReturnDtos.ReturnDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(returnService.get(CurrentUser.getShopId(), id)));
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<ReturnDtos.ReturnPhotoResponse>>> addPhotos(
            @PathVariable Long id, @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(returnService.addPhotos(CurrentUser.getShopId(), id, files)));
    }

    @GetMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Resource> photo(@PathVariable Long id, @PathVariable Long photoId) {
        ReturnService.PhotoDownload photo = returnService.getPhoto(CurrentUser.getShopId(), id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(photo.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.resource());
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<ReturnDtos.ReturnDetailResponse>> review(
            @PathVariable Long id, @RequestBody(required = false) ReturnDtos.ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Return moved to review",
                returnService.review(CurrentUser.getShopId(), id, request)));
    }

    /** Resolving can pay money out and move stock, so it is restricted to the owner. */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ReturnDtos.ReturnDetailResponse>> resolve(
            @PathVariable Long id, @Valid @RequestBody ReturnDtos.ResolveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Return resolved",
                returnService.resolve(CurrentUser.getShopId(), CurrentUser.getUserId(), id, request)));
    }
}
